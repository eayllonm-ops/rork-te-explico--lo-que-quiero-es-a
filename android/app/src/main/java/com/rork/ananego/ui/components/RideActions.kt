package com.rork.ananego.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.rork.ananego.data.model.Ride
import com.rork.ananego.ui.state.AppViewModel

private const val TAG = "RideActions"

/**
 * Quick-contact and safety shortcuts for an active ride. Everything goes through
 * system intents (dialer, WhatsApp link), so no extra permissions are needed.
 */
object RideActions {

    /** Normalizes a Peruvian mobile to the international form WhatsApp expects (51XXXXXXXXX). */
    fun international(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 11 && digits.startsWith("51") -> digits
            digits.length == 9 -> "51$digits"
            else -> digits
        }
    }

    /** Opens the dialer pre-filled with the driver's number (no CALL_PHONE permission). */
    fun call(context: Context, phone: String): Boolean =
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:+${international(phone)}")))

    /** Opens a WhatsApp chat with the driver. */
    fun whatsapp(context: Context, phone: String, message: String = ""): Boolean {
        val text = if (message.isBlank()) "" else "?text=${Uri.encode(message)}"
        return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${international(phone)}$text")))
    }

    /** Opens WhatsApp's contact picker with a ready-to-send trip summary for a relative. */
    fun shareTrip(context: Context, ride: Ride): Boolean {
        val uri = Uri.parse("https://wa.me/?text=${Uri.encode(tripMessage(ride))}")
        if (launch(context, Intent(Intent.ACTION_VIEW, uri))) return true
        // No WhatsApp or browser handler: fall back to the system share sheet.
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, tripMessage(ride))
        }
        return launch(context, Intent.createChooser(share, "Compartir viaje"))
    }

    /** Plain-text summary: driver, vehicle, plate and route. */
    fun tripMessage(ride: Ride): String {
        val driver = ride.driver
        val pickup = ride.origin.position
        return buildString {
            appendLine("Estoy en un viaje con Añane Go.")
            if (driver != null) {
                appendLine("Conductor: ${driver.name}")
                appendLine("Vehículo: ${driver.vehicleLabel}")
                appendLine("Placa: ${driver.plate}")
            }
            appendLine("Ruta: ${ride.origin.name} → ${ride.destination.name}")
            if (ride.reference.isNotBlank()) appendLine("Referencia: ${ride.reference}")
            appendLine("Tarifa: ${AppViewModel.formatSoles(ride.fareSoles)} (${ride.paymentMethod.label})")
            append("Punto de recojo: https://maps.google.com/?q=${pickup.latitude},${pickup.longitude}")
        }
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (error: ActivityNotFoundException) {
        Log.w(TAG, "No app can handle ${intent.action}")
        false
    }
}
