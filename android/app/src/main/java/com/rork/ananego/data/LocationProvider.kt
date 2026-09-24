package com.rork.ananego.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rork.ananego.data.model.Coordinate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "LocationProvider"

/**
 * Wraps the fused location provider so the app consumes the device GPS as a
 * simple [Flow] of [Coordinate] values.
 */
class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    val hasPermission: Boolean
        get() = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Last cached fix, useful to render the map immediately on launch. */
    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): Coordinate? {
        if (!hasPermission) return null
        return suspendCancellableCoroutine { continuation ->
            client.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location?.let { Coordinate(it.latitude, it.longitude) })
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "No se pudo leer la última ubicación: ${error.message}")
                    continuation.resume(null)
                }
        }
    }

    /** Continuous high-accuracy updates while collected. */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMillis: Long = 4_000L): Flow<Coordinate> = callbackFlow {
        if (!hasPermission) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(Coordinate(location.latitude, location.longitude))
                }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    Log.w(TAG, "Fallo al solicitar ubicación: ${error.message}")
                    close()
                }
        } catch (security: SecurityException) {
            Log.w(TAG, "Permiso de ubicación revocado: ${security.message}")
            close()
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
