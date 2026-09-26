package com.rork.ananego.data.model

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Who the person is using the app as right now. */
enum class AppRole { PASSENGER, DRIVER }

/** Kind of unit that serves a trip. */
enum class VehicleType(val label: String, val weeklyFeeSoles: Int) {
    MOTOTAXI("Mototaxi", 5),
    INTERCITY_CAR("Auto interprovincial", 10)
}

/** Service the passenger picks on the home screen. */
enum class ServiceKind(val title: String, val subtitle: String) {
    LOCAL_MOTOTAXI("Mototaxi local", "Rápido y seguro en Satipo"),
    INTERCITY("Auto de ruta", "Tarifa plana: Mazamari, Pangoa, Pichanaqui, La Merced, Huancayo, Lima")
}

/** How the passenger settles the fare with the driver (no in-app charge). */
enum class PaymentMethod(val label: String) {
    CASH("Efectivo"),
    YAPE_PLIN("Yape / Plin")
}

/** Special-attention options a passenger can flag for a trip. */
enum class RidePreference(val label: String) {
    WHEELCHAIR("Silla de ruedas"),
    LUGGAGE("Con equipaje"),
    ELDERLY("Adulto mayor"),
    CHILD_SEAT("Viaja con niño"),
    PET("Con mascota")
}

/**
 * A real world position in decimal degrees. Replaces the previous fractional
 * canvas coordinate so every marker can be drawn on Google Maps directly.
 */
data class Coordinate(val latitude: Double, val longitude: Double) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)

    /** Great-circle distance in kilometers using the haversine formula. */
    fun distanceKmTo(other: Coordinate): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) *
            sin(dLon / 2) * sin(dLon / 2)
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Linear interpolation, used to animate a driver moving toward a point. */
    fun moveToward(target: Coordinate, fraction: Double): Coordinate = Coordinate(
        latitude = latitude + (target.latitude - latitude) * fraction,
        longitude = longitude + (target.longitude - longitude) * fraction
    )
}

fun LatLng.toCoordinate(): Coordinate = Coordinate(latitude, longitude)

data class Driver(
    val id: String,
    val name: String,
    val initials: String,
    val rating: Double,
    val tripCount: Int,
    val vehicleType: VehicleType,
    val plate: String,
    val etaMinutes: Int,
    val distanceKm: Double,
    val position: Coordinate,
    val isVerified: Boolean = true,
    /** True for the demo fleet the service keeps running while Satipo signs up. */
    val isSimulated: Boolean = false,
    /** Brand / model of the unit, e.g. "Bajaj RE". */
    val vehicleModel: String = "",
    /** Driver's mobile, only sent to the passenger of an assigned ride. */
    val phone: String = "",
    /** Yape / Plin number the passenger transfers to. */
    val payoutPhone: String = "",
    val hasPaymentQr: Boolean = false
) {
    /** "Mototaxi · Bajaj RE" or just the type when the model is unknown. */
    val vehicleLabel: String
        get() = if (vehicleModel.isBlank()) vehicleType.label else "${vehicleType.label} · $vehicleModel"
}

data class Place(
    val name: String,
    val detail: String,
    val position: Coordinate
)

/**
 * One live suggestion from Google Places while the passenger is still typing.
 * Has no coordinates yet — [com.rork.ananego.data.remote.PlacesRepository.resolvePlace]
 * fetches those only once the passenger actually taps a suggestion, so every
 * keystroke doesn't burn a Place Details call.
 */
data class PlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String
)

enum class RideStatus(val label: String) {
    SEARCHING("Buscando conductor"),
    ACCEPTED("Conductor en camino"),
    ARRIVED("Tu conductor llegó"),
    ON_TRIP("En viaje"),
    COMPLETED("Viaje completado"),
    CANCELLED("Viaje cancelado")
}

data class Ride(
    val id: String,
    val serviceKind: ServiceKind,
    val origin: Place,
    val destination: Place,
    val driver: Driver?,
    val fareSoles: Double,
    val status: RideStatus,
    val passengerCount: Int,
    val preferences: Set<RidePreference>,
    val createdAtLabel: String,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    /** Optional landmark the passenger typed, e.g. "portón azul". */
    val reference: String = ""
)

/** A driver's price for the passenger's open request, ready to accept. */
data class RideOffer(
    val rideId: String,
    val driver: Driver,
    val amountSoles: Double
)

/** A passenger request shown live in the driver dashboard. */
data class RideRequest(
    val id: String,
    val passengerName: String,
    val passengerInitials: String,
    val passengerRating: Double,
    val passengerVerified: Boolean,
    val originName: String,
    val destinationName: String,
    val origin: Coordinate,
    val destination: Coordinate,
    val fareSoles: Double,
    val distanceKm: Double,
    val minutesAgo: Int,
    val passengerCount: Int,
    val preferences: Set<RidePreference>,
    val serviceKind: ServiceKind,
    /** This driver's live counteroffer for the request, if any. */
    val myOfferSoles: Double? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val reference: String = ""
)

enum class VerificationStatus(val label: String) {
    NOT_STARTED("Sin registrar"),
    PENDING("En revisión"),
    VERIFIED("Verificado"),
    REJECTED("Rechazado")
}

/**
 * Document photos the driver captures and uploads so the review team can
 * officially verify their identity and their unit.
 */
enum class VerificationPhoto(val label: String) {
    PROFILE("Foto de perfil"),
    DNI_FRONT("DNI frente"),
    DNI_BACK("DNI reverso"),
    VEHICLE_CARD("Tarjeta de propiedad"),
    PLATE("Placa del vehículo"),
    /** Optional: the driver's Yape / Plin QR, shown to passengers to pay. */
    PAYMENT_QR("QR de Yape / Plin")
}

data class DriverProfile(
    val fullName: String = "",
    val dni: String = "",
    val plate: String = "",
    val vehicleType: VehicleType = VehicleType.MOTOTAXI,
    val hasProfilePhoto: Boolean = false,
    val hasDniFront: Boolean = false,
    val hasDniBack: Boolean = false,
    val hasVehicleCard: Boolean = false,
    val hasPlatePhoto: Boolean = false,
    val status: VerificationStatus = VerificationStatus.NOT_STARTED,
    /** Why the last review was rejected, shown so the driver can fix it. */
    val rejectionReason: String = "",
    /** Mobile the passenger calls or messages on WhatsApp. */
    val phone: String = "",
    val vehicleModel: String = "",
    /** Yape / Plin number; falls back to [phone] when empty. */
    val payoutPhone: String = "",
    val hasPaymentQr: Boolean = false
) {
    val isComplete: Boolean
        get() = fullName.isNotBlank() &&
            dni.length >= 8 &&
            phone.length == 9 &&
            vehicleModel.isNotBlank() &&
            plate.isNotBlank() &&
            hasProfilePhoto &&
            hasDniFront &&
            hasDniBack &&
            hasVehicleCard &&
            hasPlatePhoto
}

data class Subscription(
    val vehicleType: VehicleType,
    val isActive: Boolean,
    val daysRemaining: Int,
    val paidThisWeekSoles: Double,
    val renewsOnLabel: String
) {
    val weeklyFeeSoles: Int get() = vehicleType.weeklyFeeSoles
    val progress: Float get() = (daysRemaining.coerceIn(0, 7)) / 7f
}

data class DriverDayStats(
    val trips: Int,
    val earningsSoles: Double,
    val onlineMinutes: Int,
    val acceptanceRate: Int
)

data class PassengerProfile(
    val name: String,
    val initials: String,
    val phone: String,
    val dni: String,
    val rating: Double,
    val isVerified: Boolean,
    val defaultPassengerCount: Int,
    val defaultPreferences: Set<RidePreference>
)

/** How the device location is currently being obtained. */
enum class LocationStatus {
    IDLE,
    PERMISSION_REQUIRED,
    LOCATING,
    LIVE,
    UNAVAILABLE
}

/**
 * Price rules of the negotiation: the suggested fare the passenger starts
 * from and the floor nobody may go below.
 */
object FareRules {
    /** Quick-adjust step of the price proposal sheet. */
    const val STEP_SOLES: Double = 0.5

    /** Mototaxi base / minimum fare inside Satipo. */
    const val MOTOTAXI_BASE_SOLES: Double = 2.0

    fun floorSoles(service: ServiceKind): Double = when (service) {
        ServiceKind.LOCAL_MOTOTAXI -> MOTOTAXI_BASE_SOLES
        ServiceKind.INTERCITY -> 5.0
    }

    /**
     * Flat Satipo route fares (autos de ruta). Matched by keyword so
     * "San Martín de Pangoa" or "Pichanaki" hit the right price. Loaded as
     * direct values — no Directions / Distance Matrix call is ever made.
     */
    private val fixedIntercityFares: List<Pair<List<String>, Double>> = listOf(
        listOf("mazamari") to 5.0,
        listOf("pangoa") to 8.0,
        listOf("pichanaqui", "pichanaki") to 20.0,
        listOf("la merced") to 40.0,
        listOf("huancayo") to 90.0,
        listOf("lima") to 140.0
    )

    /** Flat fare for a known route, or null when the destination has no fixed price. */
    fun fixedIntercitySoles(destinationName: String): Double? {
        val name = destinationName.trim().lowercase()
        return fixedIntercityFares.firstOrNull { (keys, _) ->
            keys.any { key -> Regex("\\b${Regex.escape(key)}\\b").containsMatchIn(name) }
        }?.second
    }

    fun suggestedSoles(service: ServiceKind, destinationName: String, distanceKm: Double): Double {
        return when (service) {
            // S/ 2.00 covers the first 1.5 km, then S/ 1.00 per extra km.
            ServiceKind.LOCAL_MOTOTAXI ->
                round((MOTOTAXI_BASE_SOLES + (distanceKm - 1.5).coerceAtLeast(0.0)).coerceIn(MOTOTAXI_BASE_SOLES, 15.0))
            ServiceKind.INTERCITY ->
                fixedIntercitySoles(destinationName)
                    ?: round((5.0 + distanceKm * 0.45).coerceAtLeast(5.0))
        }
    }

    /** Snaps an amount to the nearest S/ 0.50 and lifts it above the floor. */
    fun clamp(amount: Double, service: ServiceKind): Double {
        val safe = if (amount.isNaN()) floorSoles(service) else amount
        val snapped = (safe * 2).roundToInt() / 2.0
        return maxOf(snapped, floorSoles(service))
    }

    private fun round(value: Double): Double = (value * 2).roundToInt() / 2.0
}
