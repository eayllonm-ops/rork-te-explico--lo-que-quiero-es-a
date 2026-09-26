package com.rork.ananego.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of the Añane Go backend. These mirror the JSON produced by the
 * city Durable Object and are mapped into the UI models by the view model.
 */
@Serializable
data class NetworkSnapshot(
    val serverTime: Long = 0L,
    val profile: NetworkProfile = NetworkProfile(),
    val drivers: List<NetworkDriver> = emptyList(),
    val openRides: List<NetworkRide> = emptyList(),
    val offers: List<NetworkOffer> = emptyList(),
    val activeRide: NetworkRide? = null,
    val history: List<NetworkRide> = emptyList(),
    val todayTrips: Int = 0,
    val todayEarnings: Double = 0.0,
    val onlineDrivers: Int = 0
)

@Serializable
data class NetworkProfile(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val dni: String = "",
    val rating: Double = 5.0,
    val role: String = "PASSENGER",
    val verified: Boolean = false,
    val defaultPassengerCount: Int = 1,
    val defaultPreferences: List<String> = emptyList(),
    val vehicleType: String = "MOTOTAXI",
    val plate: String = "",
    val driverStatus: String = "NOT_STARTED",
    val isOnline: Boolean = false,
    val subscriptionActive: Boolean = false,
    val subscriptionDaysLeft: Int = 0,
    val subscriptionExpiresAt: Long = 0L,
    val paidThisWeek: Double = 0.0,
    val uploadedPhotos: List<String> = emptyList(),
    val rejectionReason: String = "",
    val vehicleModel: String = "",
    val payoutPhone: String = ""
)

@Serializable
data class NetworkDriver(
    val id: String = "",
    val name: String = "",
    val initials: String = "AG",
    val rating: Double = 5.0,
    val tripCount: Int = 0,
    val vehicleType: String = "MOTOTAXI",
    val plate: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val distanceKm: Double = 0.0,
    val etaMinutes: Int = 1,
    val verified: Boolean = true,
    val simulated: Boolean = false,
    val vehicleModel: String = "",
    val phone: String = "",
    val payoutPhone: String = "",
    val hasPaymentQr: Boolean = false
)

@Serializable
data class NetworkRide(
    val id: String = "",
    val passengerId: String = "",
    val passengerName: String = "",
    val passengerInitials: String = "AG",
    val passengerRating: Double = 5.0,
    val passengerVerified: Boolean = false,
    val serviceKind: String = "LOCAL_MOTOTAXI",
    val originName: String = "",
    val originLat: Double = 0.0,
    val originLng: Double = 0.0,
    val destName: String = "",
    val destDetail: String = "",
    val destLat: Double = 0.0,
    val destLng: Double = 0.0,
    val fare: Double = 0.0,
    val distanceKm: Double = 0.0,
    val status: String = "SEARCHING",
    val passengerCount: Int = 1,
    val preferences: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val driver: NetworkDriver? = null,
    val myOffer: Double? = null,
    val isMine: Boolean = false,
    val paymentMethod: String = "CASH",
    val reference: String = ""
)

/** A driver's counteroffer on the viewer's open request. */
@Serializable
data class NetworkOffer(
    val rideId: String = "",
    val driverId: String = "",
    val amount: Double = 0.0,
    val driver: NetworkDriver? = null
)

/** Envelope returned by the HTTP command routes. */
@Serializable
data class CommandResponse(
    val ok: Boolean = false,
    val notice: String? = null,
    val snapshot: NetworkSnapshot? = null,
    val error: String? = null
)

/** One pending document review shown in the admin console. */
@Serializable
data class NetworkAdminDriver(
    val id: String = "",
    val name: String = "",
    val dni: String = "",
    val plate: String = "",
    val vehicleType: String = "MOTOTAXI",
    val driverStatus: String = "PENDING",
    val rejectionReason: String = "",
    val submittedAt: Long = 0L,
    val photos: List<String> = emptyList()
)

/** Response of the PIN-protected admin-list route. */
@Serializable
data class NetworkAdminList(
    val ok: Boolean = false,
    val error: String? = null,
    val pending: List<NetworkAdminDriver> = emptyList()
)

/** Realtime frames pushed down the socket that are not full snapshots. */
@Serializable
data class NoticeFrame(
    @SerialName("type") val type: String = "",
    val message: String = ""
)

/** Health of the realtime link, surfaced in the UI. */
enum class ConnectionStatus { CONNECTING, LIVE, OFFLINE }
