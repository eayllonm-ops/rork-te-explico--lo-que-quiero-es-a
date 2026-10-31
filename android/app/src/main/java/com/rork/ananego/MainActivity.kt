package com.rork.ananego

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rork.ananego.ui.navigation.AppNavigation
import com.rork.ananego.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installMapCrashGuard()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNavigation()
            }
        }
    }

    /**
     * On devices where the Google Maps backend is a replacement service
     * (emulators and microG builds), pausing a map whose LocationComponent was
     * never activated throws LocationComponentNotInitializedException from the
     * provider itself — even when the app never enables the my-location layer.
     * It is thrown during Activity.onPause and would kill the process, so we
     * install an uncaught-exception guard that swallows exactly that exception
     * and delegates everything else to the previous handler. Real devices with
     * Google Play Services never hit this path.
     */
    private fun installMapCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable.hasCauseNamed("com.mapbox.mapboxsdk.location.LocationComponentNotInitializedException")) {
                Log.w("AnaneGuard", "Ignorado: el mapa del dispositivo no activó su LocationComponent al pausar", throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun Throwable.hasCauseNamed(className: String): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current::class.java.name == className) return true
            current = current.cause
        }
        return false
    }
}
