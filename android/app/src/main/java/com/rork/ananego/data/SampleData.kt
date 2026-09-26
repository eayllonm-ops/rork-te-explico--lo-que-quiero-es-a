package com.rork.ananego.data

import com.rork.ananego.data.model.Coordinate
import com.rork.ananego.data.model.Driver
import com.rork.ananego.data.model.DriverDayStats
import com.rork.ananego.data.model.DriverProfile
import com.rork.ananego.data.model.PassengerProfile
import com.rork.ananego.data.model.Place
import com.rork.ananego.data.model.RidePreference
import com.rork.ananego.data.model.RideRequest
import com.rork.ananego.data.model.ServiceKind
import com.rork.ananego.data.model.Subscription
import com.rork.ananego.data.model.VehicleType
import com.rork.ananego.data.model.VerificationStatus
import kotlin.math.ceil
import kotlin.math.max

/**
 * Real Satipo geography plus the driver/request seeds that are placed around
 * the device's live GPS position.
 */
object SampleData {

    /** Plaza de Armas de Satipo — fallback center before the first GPS fix. */
    val satipoCenter = Coordinate(-11.25283, -74.63757)

    val localDestinations: List<Place> = listOf(
        Place("Terminal Terrestre Satipo", "Av. Marginal 480", Coordinate(-11.25717, -74.63301)),
        Place("Mercado Central de Satipo", "Jr. Colonos Fundadores", Coordinate(-11.25470, -74.63925)),
        Place("Hospital de Satipo", "Jr. Los Incas 210", Coordinate(-11.24952, -74.64108)),
        Place("AH Micaela Bastidas", "Zona sur de Satipo", Coordinate(-11.26041, -74.64216)),
        Place("Universidad UNCP Satipo", "Carretera Marginal km 2", Coordinate(-11.24398, -74.62802)),
        Place("AH San Martín", "Zona norte de Satipo", Coordinate(-11.24612, -74.63509))
    )

    val intercityDestinations: List<Place> = listOf(
        Place("Mazamari", "Tarifa plana S/ 5.00 · aprox. 35 min", Coordinate(-11.32361, -74.52834)),
        Place("San Martín de Pangoa", "Tarifa plana S/ 8.00 · aprox. 50 min", Coordinate(-11.40085, -74.54752)),
        Place("Pichanaqui", "Tarifa plana S/ 20.00 · aprox. 1 h 30 min", Coordinate(-10.92835, -74.87038)),
        Place("La Merced", "Tarifa plana S/ 40.00 · aprox. 2 h 40 min", Coordinate(-11.05754, -75.33784)),
        Place("Huancayo", "Tarifa plana S/ 90.00 · aprox. 6 h", Coordinate(-12.06513, -75.20486)),
        Place("Lima", "Tarifa plana S/ 140.00 · aprox. 10 h", Coordinate(-12.04637, -77.04279))
    )

    /** Average city speeds used to turn a real distance into an ETA. */
    private fun averageSpeedKmh(vehicleType: VehicleType): Double = when (vehicleType) {
        VehicleType.MOTOTAXI -> 19.0
        VehicleType.INTERCITY_CAR -> 42.0
    }

    fun etaMinutes(distanceKm: Double, vehicleType: VehicleType): Int =
        max(1, ceil(distanceKm / averageSpeedKmh(vehicleType) * 60).toInt())

    private data class DriverSeed(
        val id: String,
        val name: String,
        val initials: String,
        val rating: Double,
        val tripCount: Int,
        val vehicleType: VehicleType,
        val plate: String,
        val offsetLat: Double,
        val offsetLng: Double
    )

    private val driverSeeds: List<DriverSeed> = listOf(
        DriverSeed("d1", "Jhon R.", "JR", 4.9, 128, VehicleType.MOTOTAXI, "AB-1234", 0.0042, -0.0031),
        DriverSeed("d2", "Marco T.", "MT", 4.8, 96, VehicleType.MOTOTAXI, "CD-5567", -0.0038, 0.0049),
        DriverSeed("d3", "Luis Q.", "LQ", 4.7, 211, VehicleType.MOTOTAXI, "EF-8890", 0.0065, 0.0058),
        DriverSeed("d4", "Elena P.", "EP", 5.0, 74, VehicleType.INTERCITY_CAR, "GH-2210", -0.0091, -0.0064),
        DriverSeed("d5", "Raúl C.", "RC", 4.6, 152, VehicleType.INTERCITY_CAR, "IJ-4477", 0.0118, -0.0092)
    )

    /** Places the available fleet around the passenger's live position. */
    fun driversAround(center: Coordinate): List<Driver> = driverSeeds.map { seed ->
        val position = Coordinate(
            latitude = center.latitude + seed.offsetLat,
            longitude = center.longitude + seed.offsetLng
        )
        val distanceKm = center.distanceKmTo(position)
        Driver(
            id = seed.id,
            name = seed.name,
            initials = seed.initials,
            rating = seed.rating,
            tripCount = seed.tripCount,
            vehicleType = seed.vehicleType,
            plate = seed.plate,
            etaMinutes = etaMinutes(distanceKm, seed.vehicleType),
            distanceKm = distanceKm,
            position = position
        )
    }

    private data class RequestSeed(
        val id: String,
        val passengerName: String,
        val passengerInitials: String,
        val passengerRating: Double,
        val passengerVerified: Boolean,
        val originName: String,
        val destinationName: String,
        val originOffsetLat: Double,
        val originOffsetLng: Double,
        val fareSoles: Double,
        val minutesAgo: Int,
        val passengerCount: Int,
        val preferences: Set<RidePreference>,
        val serviceKind: ServiceKind,
        val destinationIndex: Int
    )

    private val requestSeeds: List<RequestSeed> = listOf(
        RequestSeed(
            id = "r1",
            passengerName = "Rosa M.",
            passengerInitials = "RM",
            passengerRating = 4.9,
            passengerVerified = true,
            originName = "Jr. Grau 320",
            destinationName = "Terminal Terrestre Satipo",
            originOffsetLat = 0.0026,
            originOffsetLng = 0.0018,
            fareSoles = 4.0,
            minutesAgo = 1,
            passengerCount = 1,
            preferences = setOf(RidePreference.LUGGAGE),
            serviceKind = ServiceKind.LOCAL_MOTOTAXI,
            destinationIndex = 0
        ),
        RequestSeed(
            id = "r2",
            passengerName = "Carlos T.",
            passengerInitials = "CT",
            passengerRating = 4.7,
            passengerVerified = true,
            originName = "Av. Micaela Bastidas",
            destinationName = "Mercado Central de Satipo",
            originOffsetLat = -0.0047,
            originOffsetLng = -0.0029,
            fareSoles = 6.0,
            minutesAgo = 3,
            passengerCount = 2,
            preferences = emptySet(),
            serviceKind = ServiceKind.LOCAL_MOTOTAXI,
            destinationIndex = 1
        ),
        RequestSeed(
            id = "r3",
            passengerName = "María L.",
            passengerInitials = "ML",
            passengerRating = 5.0,
            passengerVerified = true,
            originName = "Jr. Los Incas",
            destinationName = "Hospital de Satipo",
            originOffsetLat = 0.0033,
            originOffsetLng = -0.0052,
            fareSoles = 5.0,
            minutesAgo = 4,
            passengerCount = 1,
            preferences = setOf(RidePreference.WHEELCHAIR, RidePreference.ELDERLY),
            serviceKind = ServiceKind.LOCAL_MOTOTAXI,
            destinationIndex = 2
        ),
        RequestSeed(
            id = "r4",
            passengerName = "Pedro S.",
            passengerInitials = "PS",
            passengerRating = 4.5,
            passengerVerified = false,
            originName = "Satipo centro",
            destinationName = "Pichanaqui",
            originOffsetLat = -0.0012,
            originOffsetLng = 0.0024,
            fareSoles = 20.0,
            minutesAgo = 6,
            passengerCount = 3,
            preferences = setOf(RidePreference.LUGGAGE),
            serviceKind = ServiceKind.INTERCITY,
            destinationIndex = 2
        )
    )

    /** Builds the live request feed relative to the driver's own GPS position. */
    fun requestsAround(center: Coordinate): List<RideRequest> = requestSeeds.map { seed ->
        val origin = Coordinate(
            latitude = center.latitude + seed.originOffsetLat,
            longitude = center.longitude + seed.originOffsetLng
        )
        val destination = when (seed.serviceKind) {
            ServiceKind.LOCAL_MOTOTAXI -> {
                val place = localDestinations[seed.destinationIndex]
                Coordinate(
                    latitude = origin.latitude + (place.position.latitude - satipoCenter.latitude),
                    longitude = origin.longitude + (place.position.longitude - satipoCenter.longitude)
                )
            }
            ServiceKind.INTERCITY -> intercityDestinations[seed.destinationIndex].position
        }
        RideRequest(
            id = seed.id,
            passengerName = seed.passengerName,
            passengerInitials = seed.passengerInitials,
            passengerRating = seed.passengerRating,
            passengerVerified = seed.passengerVerified,
            originName = seed.originName,
            destinationName = seed.destinationName,
            origin = origin,
            destination = destination,
            fareSoles = seed.fareSoles,
            distanceKm = origin.distanceKmTo(destination),
            minutesAgo = seed.minutesAgo,
            passengerCount = seed.passengerCount,
            preferences = seed.preferences,
            serviceKind = seed.serviceKind
        )
    }

    val subscription = Subscription(
        vehicleType = VehicleType.MOTOTAXI,
        isActive = true,
        daysRemaining = 5,
        paidThisWeekSoles = 5.0,
        renewsOnLabel = "Renueva el lunes 28"
    )

    val dayStats = DriverDayStats(
        trips = 8,
        earningsSoles = 62.0,
        onlineMinutes = 215,
        acceptanceRate = 92
    )

    val driverProfile = DriverProfile(
        fullName = "Juan Carlos García Torres",
        dni = "70451234",
        plate = "AB-1234",
        vehicleType = VehicleType.MOTOTAXI,
        hasProfilePhoto = true,
        hasDniFront = true,
        hasDniBack = true,
        hasVehicleCard = true,
        status = VerificationStatus.VERIFIED
    )

    val passengerProfile = PassengerProfile(
        name = "Ana Vilca",
        initials = "AV",
        phone = "+51 964 221 887",
        dni = "72884510",
        rating = 4.9,
        isVerified = true,
        defaultPassengerCount = 1,
        defaultPreferences = emptySet()
    )
}
