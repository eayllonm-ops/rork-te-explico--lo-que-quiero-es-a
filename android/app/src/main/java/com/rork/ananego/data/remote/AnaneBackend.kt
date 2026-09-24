package com.rork.ananego.data.remote

import android.util.Log
import com.rork.ananego.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "AnaneBackend"

/** A frame received from the live city stream. */
sealed interface LiveEvent {
    data class Snapshot(val snapshot: NetworkSnapshot) : LiveEvent
    data class Notice(val message: String) : LiveEvent
    data class Status(val status: ConnectionStatus) : LiveEvent
}

/**
 * Talks to the Añane Go city backend: one WebSocket carrying live snapshots of
 * drivers, requests and rides, plus HTTP commands for every user action.
 */
class AnaneBackend(private val baseUrl: String = BuildConfig.BACKEND_URL) {

    val isConfigured: Boolean = baseUrl.isNotBlank()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var socket: WebSocket? = null

    /**
     * Opens the realtime stream for this device. The flow emits connection
     * status changes, city snapshots and server notices until cancelled.
     */
    fun liveStream(userId: String, role: String): Flow<LiveEvent> = callbackFlow {
        if (!isConfigured) {
            trySend(LiveEvent.Status(ConnectionStatus.OFFLINE))
            awaitClose { }
            return@callbackFlow
        }

        trySend(LiveEvent.Status(ConnectionStatus.CONNECTING))

        val wsUrl = baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/') + "/live?userId=$userId&role=$role"

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                trySend(LiveEvent.Status(ConnectionStatus.LIVE))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = json.parseToJsonElement(text).jsonObject
                    when (root["type"]?.jsonPrimitive?.content) {
                        "notice" -> {
                            val frame = json.decodeFromString<NoticeFrame>(text)
                            trySend(LiveEvent.Notice(frame.message))
                        }
                        else -> {
                            val snapshot = json.decodeFromString<NetworkSnapshot>(text)
                            trySend(LiveEvent.Snapshot(snapshot))
                        }
                    }
                } catch (error: Exception) {
                    Log.w(TAG, "Frame inválido: ${error.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Conexión en vivo perdida: ${t.message}")
                socket = null
                trySend(LiveEvent.Status(ConnectionStatus.OFFLINE))
                // Ending the flow lets the caller back off and reconnect.
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                trySend(LiveEvent.Status(ConnectionStatus.OFFLINE))
                close()
            }
        }

        val request = Request.Builder().url(wsUrl).build()
        val ws = client.newWebSocket(request, listener)

        awaitClose {
            socket = null
            ws.close(1000, "closed")
        }
    }

    /** Streams the device position up the open socket (cheap, no HTTP hop). */
    fun pushLocation(latitude: Double, longitude: Double) {
        val payload = buildJsonObject {
            put("type", JsonPrimitive("location"))
            put("lat", JsonPrimitive(latitude))
            put("lng", JsonPrimitive(longitude))
        }
        socket?.send(payload.toString())
    }

    /** Tells the server which side of the app this device is currently using. */
    fun pushRole(role: String) {
        val payload = buildJsonObject {
            put("type", JsonPrimitive("role"))
            put("role", JsonPrimitive(role))
        }
        socket?.send(payload.toString())
    }

    /** Keeps the device marked as present between location fixes. */
    fun pushHeartbeat() {
        socket?.send("""{"type":"ping"}""")
    }

    /** Stable URL that streams a stored verification photo for preview. */
    fun verificationPhotoUrl(userId: String, kind: String): String =
        "$baseUrl/api/verification-photo?userId=$userId&kind=$kind"

    /**
     * Issues a command against the city. Returns the server response, or a
     * failure envelope with a user-facing message when the network is down.
     */
    suspend fun command(action: String, body: JsonObject): CommandResponse {
        val text = postJson(action, body) ?: return CommandResponse(
            ok = false,
            notice = "Sin conexión. Intenta de nuevo."
        )
        return try {
            json.decodeFromString<CommandResponse>(text)
        } catch (error: Exception) {
            Log.w(TAG, "Respuesta inválida de $action: ${error.message}")
            CommandResponse(ok = false, notice = "Respuesta inesperada del servidor")
        }
    }

    /** Lists the drivers waiting for a document review (PIN-gated). */
    suspend fun adminList(pin: String): NetworkAdminList {
        val text = postJson(
            "admin-list",
            buildJsonObject { put("pin", JsonPrimitive(pin)) }
        ) ?: return NetworkAdminList(error = "Sin conexión. Intenta de nuevo.")
        return try {
            json.decodeFromString<NetworkAdminList>(text)
        } catch (error: Exception) {
            Log.w(TAG, "Respuesta inválida de admin-list: ${error.message}")
            NetworkAdminList(error = "Respuesta inesperada del servidor")
        }
    }

    /** Unlocks the admin console; ok=true when the PIN matches. */
    suspend fun adminLogin(pin: String): Boolean {
        val response = command("admin-login", buildJsonObject { put("pin", JsonPrimitive(pin)) })
        return response.ok
    }

    /** Approves or rejects a driver's documents from the admin console. */
    suspend fun adminDecide(
        pin: String,
        targetUserId: String,
        approve: Boolean,
        reason: String
    ): CommandResponse = command(
        "admin-decide",
        buildJsonObject {
            put("pin", JsonPrimitive(pin))
            put("targetUserId", JsonPrimitive(targetUserId))
            put("decision", JsonPrimitive(if (approve) "approve" else "reject"))
            if (!approve) put("reason", JsonPrimitive(reason))
        }
    )

    /** Posts a JSON command and returns the raw body, or null when offline. */
    private suspend fun postJson(action: String, body: JsonObject): String? {
        if (!isConfigured) return null
        val request = Request.Builder()
            .url("$baseUrl/api/$action")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Comando $action falló: ${e.message}")
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val text = response.body?.string().orEmpty()
                    response.close()
                    if (continuation.isActive) continuation.resume(text)
                }
            })
        }
    }
}
