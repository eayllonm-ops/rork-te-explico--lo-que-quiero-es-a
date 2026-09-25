package com.rork.ananego.data.remote

import android.content.Context
import android.util.Log
import com.rork.ananego.BuildConfig
import com.rork.ananego.data.installedSigningSha1
import com.rork.ananego.data.model.Coordinate
import com.rork.ananego.data.model.Place
import com.rork.ananego.data.model.PlacePrediction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "PlacesRepository"
private const val PLACES_BASE = "https://places.googleapis.com/v1/places"

/**
 * Turns what the passenger types into real streets and points of interest via
 * the Places API (New), instead of the fixed sample list in [com.rork.ananego.data.SampleData].
 *
 * Reuses the same Maps API key already configured for the map (restricted in
 * Google Cloud to this app's package name + SHA-1 fingerprint). A restricted
 * key normally only works from within the Maps/Places Android SDK, which adds
 * the app's identity automatically — since this talks to the REST endpoint
 * directly with OkHttp instead, [installedSigningSha1] fingerprint is sent by
 * hand via the X-Android-Package / X-Android-Cert headers so the same
 * restricted key is accepted.
 */
class PlacesRepository(context: Context) {

    private val apiKey = BuildConfig.MAPS_API_KEY
    val isConfigured: Boolean = apiKey.isNotBlank()

    private val packageName = context.packageName
    private val androidCert = installedSigningSha1(context)?.replace(":", "")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** One token shared by autocomplete keystrokes + the final details call, so Google bills the whole search as a single session. */
    private var sessionToken: String = UUID.randomUUID().toString()

    private fun startNewSession() {
        sessionToken = UUID.randomUUID().toString()
    }

    /** Finds addresses and places matching [query], biased toward [bias] (the rider's current map center). */
    suspend fun autocomplete(query: String, bias: Coordinate): List<PlacePrediction> {
        if (!isConfigured || query.isBlank()) return emptyList()
        val body = buildJsonObject {
            put("input", JsonPrimitive(query))
            put("sessionToken", JsonPrimitive(sessionToken))
            put("languageCode", JsonPrimitive("es"))
            put("regionCode", JsonPrimitive("PE"))
            put(
                "locationBias",
                buildJsonObject {
                    put(
                        "circle",
                        buildJsonObject {
                            put(
                                "center",
                                buildJsonObject {
                                    put("latitude", JsonPrimitive(bias.latitude))
                                    put("longitude", JsonPrimitive(bias.longitude))
                                }
                            )
                            put("radius", JsonPrimitive(50000.0))
                        }
                    )
                }
            )
        }
        val text = post("$PLACES_BASE:autocomplete", body) ?: return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val suggestions = root["suggestions"]?.jsonArray ?: return emptyList()
            suggestions.mapNotNull { suggestion ->
                val prediction = suggestion.jsonObject["placePrediction"]?.jsonObject ?: return@mapNotNull null
                val placeId = prediction["placeId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val structured = prediction["structuredFormat"]?.jsonObject
                val primary = structured?.get("mainText")?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: prediction["text"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                val secondary = structured?.get("secondaryText")?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
                PlacePrediction(placeId = placeId, primaryText = primary, secondaryText = secondary)
            }
        }.onFailure { Log.w(TAG, "No se pudo leer las sugerencias: ${it.message}") }.getOrElse { emptyList() }
    }

    /** Resolves one suggestion into a real [Place] with coordinates, then closes the billing session. */
    suspend fun resolvePlace(prediction: PlacePrediction): Place? {
        if (!isConfigured) return null
        val text = getWithFieldMask(
            "$PLACES_BASE/${prediction.placeId}",
            fieldMask = "location,formattedAddress,displayName"
        )
        startNewSession()
        if (text == null) return null
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val location = root["location"]?.jsonObject ?: return null
            val lat = location["latitude"]?.jsonPrimitive?.double ?: return null
            val lng = location["longitude"]?.jsonPrimitive?.double ?: return null
            val name = root["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: prediction.primaryText
            val address = root["formattedAddress"]?.jsonPrimitive?.content ?: prediction.secondaryText
            Place(name = name, detail = address, position = Coordinate(lat, lng))
        }.onFailure { Log.w(TAG, "No se pudo resolver el lugar: ${it.message}") }.getOrNull()
    }

    private fun baseRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url).header("X-Goog-Api-Key", apiKey)
        if (packageName.isNotBlank()) builder.header("X-Android-Package", packageName)
        if (!androidCert.isNullOrBlank()) builder.header("X-Android-Cert", androidCert)
        return builder
    }

    private suspend fun post(url: String, body: JsonObject): String? =
        suspendCancellableCoroutine { continuation ->
            val request = baseRequest(url).post(body.toString().toRequestBody(jsonMedia)).build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Autocompletado falló: ${e.message}")
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val text = response.body?.string()
                    if (!response.isSuccessful) Log.w(TAG, "Autocompletado HTTP ${response.code}: $text")
                    response.close()
                    if (continuation.isActive) continuation.resume(if (response.isSuccessful) text else null)
                }
            })
        }

    private suspend fun getWithFieldMask(url: String, fieldMask: String): String? =
        suspendCancellableCoroutine { continuation ->
            val request = baseRequest(url).header("X-Goog-FieldMask", fieldMask).get().build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Detalle de lugar falló: ${e.message}")
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val text = response.body?.string()
                    if (!response.isSuccessful) Log.w(TAG, "Detalle de lugar HTTP ${response.code}: $text")
                    response.close()
                    if (continuation.isActive) continuation.resume(if (response.isSuccessful) text else null)
                }
            })
        }
}
