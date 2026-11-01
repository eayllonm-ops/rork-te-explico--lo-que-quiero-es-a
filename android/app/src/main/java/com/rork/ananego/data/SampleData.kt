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
        Place("Terminal Terrestre Satipo", "Av. Marginal 480", Coordinate(-11.25996, -74.64461)),
        Place("Mercado Central de Satipo", "Jr. Colonos Fundadores", Coordinate(-11.25470, -74.63925)),
        Place("Hospital de Satipo", "Jr. Los Incas 210", Coordinate(-11.24931, -74.63859)),
        Place("AH Micaela Bastidas", "Zona sur de Satipo", Coordinate(-11.26041, -74.64216)),
        Place("Universidad UNCP Satipo", "Carretera Marginal km 2", Coordinate(-11.24398, -74.62802)),
        Place("AH San Martín", "Zona norte de Satipo", Coordinate(-11.24612, -74.63509)),
        // Negocios locales conocidos (Google Maps / OpenStreetMap), útiles como referencias.
        Place("Restaurante Toro Bravo", "Parrillas y ahumados · Jr. Agricultura 642", Coordinate(-11.25246, -74.64241)),
        Place("Pollería Koquetos", "Pollería · centro de Satipo", Coordinate(-11.25624, -74.63918)),
        Place("Cevichería Puntarhena", "Cevichería · centro de Satipo", Coordinate(-11.25690, -74.63928)),
        Place("Restaurant Turístico Katari", "Restaurante turístico", Coordinate(-11.25647, -74.63972)),
        Place("Recreo Turístico Mana", "Recreo campestre · ruta al río", Coordinate(-11.26118, -74.63842)),
        Place("Hospedaje Henrys", "Hospedaje · Jr. Francisco Bolognesi 460", Coordinate(-11.25063, -74.63783)),
        Place("Grifo Vásquez", "Grifo · Jr. Daniel Alcides Carrión", Coordinate(-11.24791, -74.63719)),
        Place("Zoco Market", "Minimarket · Jr. Manuel Prado", Coordinate(-11.25394, -74.63794))
    )

    /** Reference places inside the Mazamari district, verified against map data. */
    val mazamariDestinations: List<Place> = listOf(
        Place("Plaza de Armas de Mazamari", "Centro de Mazamari", Coordinate(-11.32881, -74.53069)),
        Place("Mercado de Mazamari", "Mercado central", Coordinate(-11.32607, -74.53204)),
        Place("Puesto de Salud Mazamari", "Centro de salud", Coordinate(-11.32431, -74.52906)),
        Place("Aeropuerto de Mazamari", "Aeropuerto Manuel Prado Ugarteche", Coordinate(-11.32633, -74.53534)),
        Place("Restaurante Turístico La Orilla", "Platos típicos · junto al puente San Cristóbal", Coordinate(-11.33357, -74.52862))
    )

    /** Reference places inside San Martín de Pangoa, verified against map data. */
    val pangoaDestinations: List<Place> = listOf(
        Place("Plaza de Armas de Pangoa", "Centro de San Martín de Pangoa", Coordinate(-11.42842, -74.48858)),
        Place("Hospital de San Martín de Pangoa", "Por la carretera JU-754", Coordinate(-11.42917, -74.49266)),
        Place("CAC Pangoa", "Cooperativa café Pangoa", Coordinate(-11.42633, -74.49100)),
        Place("Colegio San Ramón", "IE San Ramón, norte del pueblo", Coordinate(-11.43600, -74.48420)),
        Place("Hotel Fortuna", "Hotel · Av. España 247", Coordinate(-11.42788, -74.48937)),
        Place("Residencial Hotel Pangoa", "Hotel · entrada norte del pueblo", Coordinate(-11.41931, -74.48955)),
        Place("Restaurante La Murralla de Fierro", "Restaurante · Jr. 28 de Julio", Coordinate(-11.42657, -74.48973)),
        Place("Hospedaje Leopardo", "Hospedaje · centro de Pangoa", Coordinate(-11.42988, -74.48741))
    )

    /**
     * A town where the mototaxi service operates with its own reference places.
     * The passenger's zone follows their GPS, so someone in Mazamari sees
     * Mazamari's places, distances and search results without any setting.
     */
    data class ServiceZone(
        val name: String,
        val center: Coordinate,
        val fallbackOrigin: String,
        val localPlaces: List<Place>
    )

    private val mazamariCenter = Coordinate(-11.32881, -74.53069)
    private val pangoaCenter = Coordinate(-11.42842, -74.48858)

    val satipoZone = ServiceZone("Satipo", satipoCenter, "Plaza de Armas de Satipo", localDestinations)
    val mazamariZone = ServiceZone("Mazamari", mazamariCenter, "Plaza de Armas de Mazamari", mazamariDestinations)
    val pangoaZone = ServiceZone("Pangoa", pangoaCenter, "Plaza de Armas de Pangoa", pangoaDestinations)

    private val serviceZones = listOf(satipoZone, mazamariZone, pangoaZone)

    /** The zone whose center is closest to [position]; Satipo when nothing is known. */
    fun zoneFor(position: Coordinate?): ServiceZone =
        position?.let { pos -> serviceZones.minByOrNull { it.center.distanceKmTo(pos) } } ?: satipoZone

    val intercityDestinations: List<Place> = listOf(
        Place("Mazamari", "Tarifa plana S/ 5.00 · aprox. 35 min", Coordinate(-11.32361, -74.52834)),
        Place("San Martín de Pangoa", "Tarifa plana S/ 8.00 · aprox. 50 min", Coordinate(-11.42842, -74.48858)),
        Place("Pichanaqui", "Tarifa plana S/ 20.00 · aprox. 1 h 30 min", Coordinate(-10.92835, -74.87038)),
        Place("La Merced", "Tarifa plana S/ 40.00 · aprox. 2 h 40 min", Coordinate(-11.05606, -75.32826)),
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
