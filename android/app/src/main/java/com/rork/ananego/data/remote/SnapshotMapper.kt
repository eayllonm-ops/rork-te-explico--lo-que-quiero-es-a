package com.rork.ananego.data.remote

import com.rork.ananego.data.model.Coordinate
import com.rork.ananego.data.model.Driver
import com.rork.ananego.data.model.DriverProfile
import com.rork.ananego.data.model.PassengerProfile
import com.rork.ananego.data.model.Place
import com.rork.ananego.data.model.Ride
import com.rork.ananego.data.model.RideOffer
import com.rork.ananego.data.model.RidePreference
import com.rork.ananego.data.model.RideRequest
import com.rork.ananego.data.model.RideStatus
import com.rork.ananego.data.model.ServiceKind
import com.rork.ananego.data.model.Subscription
import com.rork.ananego.data.model.VehicleType
import com.rork.ananego.data.model.VerificationStatus
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** Converts the backend wire format into the models the UI renders. */
object SnapshotMapper {

    fun vehicleType(raw: String): VehicleType = when (raw) {
        "INTERCITY_CAR" -> VehicleType.INTERCITY_CAR
        else -> VehicleType.MOTOTAXI
    }

    fun serviceKind(raw: String): ServiceKind = when (raw) {
        "INTERCITY" -> ServiceKind.INTERCITY
        else -> ServiceKind.LOCAL_MOTOTAXI
    }

    fun rideStatus(raw: String): RideStatus = when (raw) {
        "ACCEPTED" -> RideStatus.ACCEPTED
        "ARRIVED" -> RideStatus.ARRIVED
        "ON_TRIP" -> RideStatus.ON_TRIP
        "COMPLETED" -> RideStatus.COMPLETED
        "CANCELLED" -> RideStatus.CANCELLED
        else -> RideStatus.SEARCHING
    }

    fun verificationStatus(raw: String): VerificationStatus = when (raw) {
        "PENDING" -> VerificationStatus.PENDING
        "VERIFIED" -> VerificationStatus.VERIFIED
        "REJECTED" -> VerificationStatus.REJECTED
        else -> VerificationStatus.NOT_STARTED
    }

    fun preferences(raw: List<String>): Set<RidePreference> = raw.mapNotNull { name ->
        RidePreference.entries.firstOrNull { it.name == name }
    }.toSet()

    fun driver(dto: NetworkDriver): Driver = Driver(
        id = dto.id,
        name = dto.name.ifBlank { "Conductor Añane" },
        initials = dto.initials.ifBlank { "AG" },
        rating = dto.rating,
        tripCount = dto.tripCount,
        vehicleType = vehicleType(dto.vehicleType),
        plate = dto.plate.ifBlank { "Sin placa" },
        etaMinutes = max(1, dto.etaMinutes),
        distanceKm = dto.distanceKm,
        position = Coordinate(dto.lat, dto.lng),
        isVerified = dto.verified,
        isSimulated = dto.simulated
    )

    fun ride(dto: NetworkRide, serverTime: Long): Ride = Ride(
        id = dto.id,
        serviceKind = serviceKind(dto.serviceKind),
        origin = Place(
            name = dto.originName.ifBlank { "Punto de recojo" },
            detail = "Punto de recojo",
            position = Coordinate(dto.originLat, dto.originLng)
        ),
        destination = Place(
            name = dto.destName.ifBlank { "Destino" },
            detail = dto.destDetail,
            position = Coordinate(dto.destLat, dto.destLng)
        ),
        driver = dto.driver?.let { driver(it) },
        fareSoles = dto.fare,
        status = rideStatus(dto.status),
        passengerCount = dto.passengerCount,
        preferences = preferences(dto.preferences),
        createdAtLabel = relativeLabel(dto.createdAt, serverTime)
    )

    /** A ride seen from the driver side, shown in the incoming request feed. */
    fun request(dto: NetworkRide, serverTime: Long): RideRequest = RideRequest(
        id = dto.id,
        passengerName = dto.passengerName.ifBlank { "Pasajero Añane" },
        passengerInitials = dto.passengerInitials.ifBlank { "AG" },
        passengerRating = dto.passengerRating,
        passengerVerified = dto.passengerVerified,
        originName = dto.originName.ifBlank { "Punto de recojo" },
        destinationName = dto.destName.ifBlank { "Destino" },
        origin = Coordinate(dto.originLat, dto.originLng),
        destination = Coordinate(dto.destLat, dto.destLng),
        fareSoles = dto.fare,
        distanceKm = dto.distanceKm,
        minutesAgo = minutesSince(dto.createdAt, serverTime),
        passengerCount = dto.passengerCount,
        preferences = preferences(dto.preferences),
        serviceKind = serviceKind(dto.serviceKind),
        myOfferSoles = dto.myOffer
    )

    /** An incoming counteroffer on the passenger's open request. */
    fun offer(dto: NetworkOffer): RideOffer? {
        val driverDto = dto.driver ?: return null
        return RideOffer(
            rideId = dto.rideId,
            driver = driver(driverDto),
            amountSoles = dto.amount
        )
    }

    fun passengerProfile(dto: NetworkProfile): PassengerProfile = PassengerProfile(
        name = dto.name.ifBlank { "Pasajero Añane" },
        initials = initialsOf(dto.name),
        phone = dto.phone.ifBlank { "Agrega tu número" },
        dni = dto.dni,
        rating = dto.rating,
        isVerified = dto.verified,
        defaultPassengerCount = dto.defaultPassengerCount.coerceIn(1, 4),
        defaultPreferences = preferences(dto.defaultPreferences)
    )

    fun driverProfile(
        dto: NetworkProfile,
        documents: com.rork.ananego.data.DeviceSession.DocumentFlags
    ): DriverProfile {
        // The server's list of uploaded document photos is the source of truth;
        // local document flags only cover the offline fallback.
        val photos = dto.uploadedPhotos.toSet()
        return DriverProfile(
            fullName = dto.name,
            dni = dto.dni,
            plate = dto.plate,
            vehicleType = vehicleType(dto.vehicleType),
            hasProfilePhoto = "PROFILE" in photos || documents.hasProfilePhoto,
            hasDniFront = "DNI_FRONT" in photos || documents.hasDniFront,
            hasDniBack = "DNI_BACK" in photos || documents.hasDniBack,
            hasVehicleCard = "VEHICLE_CARD" in photos || documents.hasVehicleCard,
            hasPlatePhoto = "PLATE" in photos,
            status = verificationStatus(dto.driverStatus),
            rejectionReason = dto.rejectionReason
        )
    }

    fun subscription(dto: NetworkProfile, serverTime: Long): Subscription = Subscription(
        vehicleType = vehicleType(dto.vehicleType),
        isActive = dto.subscriptionActive,
        daysRemaining = dto.subscriptionDaysLeft.coerceIn(0, 7),
        paidThisWeekSoles = dto.paidThisWeek,
        renewsOnLabel = renewalLabel(dto, serverTime)
    )

    private fun renewalLabel(dto: NetworkProfile, serverTime: Long): String = when {
        !dto.subscriptionActive -> "Renueva para volver a recibir viajes"
        dto.subscriptionDaysLeft <= 1 -> "Vence hoy"
        else -> "Vence en ${dto.subscriptionDaysLeft} días"
    }.also { _ -> if (dto.subscriptionExpiresAt < serverTime) Unit }

    fun initialsOf(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "AG"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts.first().first()}${parts.last().first()}".uppercase()
        }
    }

    private fun minutesSince(timestamp: Long, serverTime: Long): Int {
        if (timestamp <= 0L) return 0
        val elapsed = max(0L, serverTime - timestamp)
        return TimeUnit.MILLISECONDS.toMinutes(elapsed).toInt()
    }

    private fun relativeLabel(timestamp: Long, serverTime: Long): String {
        if (timestamp <= 0L) return "Hoy"
        val minutes = minutesSince(timestamp, serverTime)
        return when {
            minutes < 1 -> "Ahora"
            minutes < 60 -> "Hace $minutes min"
            minutes < 1440 -> "Hace ${minutes / 60} h"
            minutes < 2880 -> "Ayer"
            else -> "Hace ${minutes / 1440} días"
        }
    }
}
