package com.rork.ananego.ui.state

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.ananego.data.DeviceSession
import com.rork.ananego.data.DocumentPhotos
import com.rork.ananego.data.LocationProvider
import com.rork.ananego.data.SampleData
import com.rork.ananego.data.model.AppRole
import com.rork.ananego.data.model.Coordinate
import com.rork.ananego.data.model.Driver
import com.rork.ananego.data.model.DriverDayStats
import com.rork.ananego.data.model.DriverProfile
import com.rork.ananego.data.model.FareRules
import com.rork.ananego.data.model.LocationStatus
import com.rork.ananego.data.model.PassengerProfile
import com.rork.ananego.data.model.PaymentMethod
import com.rork.ananego.data.model.Place
import com.rork.ananego.data.model.PlacePrediction
import com.rork.ananego.data.model.Ride
import com.rork.ananego.data.model.RideOffer
import com.rork.ananego.data.model.RidePreference
import com.rork.ananego.data.model.RideRequest
import com.rork.ananego.data.model.RideStatus
import com.rork.ananego.data.model.ServiceKind
import com.rork.ananego.data.model.Subscription
import com.rork.ananego.data.model.VehicleType
import com.rork.ananego.data.model.VerificationPhoto
import com.rork.ananego.data.model.VerificationStatus
import com.rork.ananego.data.remote.AnaneBackend
import com.rork.ananego.data.remote.ConnectionStatus
import com.rork.ananego.data.remote.LiveEvent
import com.rork.ananego.data.remote.NetworkAdminDriver
import com.rork.ananego.data.remote.NetworkSnapshot
import com.rork.ananego.data.remote.PlacesRepository
import com.rork.ananego.data.remote.SATIPO_SEARCH_CENTER
import com.rork.ananego.data.remote.SnapshotMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.roundToInt

data class AppUiState(
    val role: AppRole = AppRole.PASSENGER,
    val selectedService: ServiceKind = ServiceKind.LOCAL_MOTOTAXI,
    val searchQuery: String = "",
    /** Live Google Places results for [searchQuery], resolved by [AppViewModel.updateSearchQuery]. */
    val placePredictions: List<PlacePrediction> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val userLocation: Coordinate? = null,
    val locationStatus: LocationStatus = LocationStatus.IDLE,
    val connection: ConnectionStatus = ConnectionStatus.CONNECTING,
    val nearbyDrivers: List<Driver> = emptyList(),
    val activeRide: Ride? = null,
    val rideHistory: List<Ride> = emptyList(),
    val passenger: PassengerProfile = EMPTY_PASSENGER,
    val driverProfile: DriverProfile = DriverProfile(),
    val subscription: Subscription = EMPTY_SUBSCRIPTION,
    val dayStats: DriverDayStats = DriverDayStats(0, 0.0, 0, 100),
    val liveRequests: List<RideRequest> = emptyList(),
    /** Counteroffers waiting on the passenger's open request. */
    val offers: List<RideOffer> = emptyList(),
    val isDriverOnline: Boolean = false,
    val isRequestingRide: Boolean = false,
    val uploadingPhotos: Set<VerificationPhoto> = emptySet(),
    val lastMessage: String? = null,
    /** A just-finished trip the passenger still has to pay (shows the QR / cash sheet). */
    val paymentDue: Ride? = null
) {
    /** Live GPS position when available, Satipo's plaza as a safe fallback. */
    val mapCenter: Coordinate get() = userLocation ?: SampleData.satipoCenter

    val hasLiveLocation: Boolean get() = locationStatus == LocationStatus.LIVE && userLocation != null

    val isLive: Boolean get() = connection == ConnectionStatus.LIVE

    val filteredDrivers: List<Driver>
        get() = nearbyDrivers.filter {
            when (selectedService) {
                ServiceKind.LOCAL_MOTOTAXI -> it.vehicleType == VehicleType.MOTOTAXI
                ServiceKind.INTERCITY -> it.vehicleType == VehicleType.INTERCITY_CAR
            }
        }.sortedBy { it.etaMinutes }

    val destinationOptions: List<Place>
        get() {
            val base = when (selectedService) {
                ServiceKind.LOCAL_MOTOTAXI -> SampleData.localDestinations
                ServiceKind.INTERCITY -> SampleData.intercityDestinations
            }
            if (searchQuery.isBlank()) return base
            return base.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

    /** Incoming requests are already filtered per service by the backend. */
    val driverRequests: List<RideRequest> get() = liveRequests

    companion object {
        val EMPTY_PASSENGER = PassengerProfile(
            name = "Pasajero Añane",
            initials = "AG",
            phone = "Agrega tu número",
            dni = "",
            rating = 5.0,
            isVerified = false,
            defaultPassengerCount = 1,
            defaultPreferences = emptySet()
        )
        val EMPTY_SUBSCRIPTION = Subscription(
            vehicleType = VehicleType.MOTOTAXI,
            isActive = false,
            daysRemaining = 0,
            paidThisWeekSoles = 0.0,
            renewsOnLabel = "Sin suscripción activa"
        )
    }
}

/**
 * Single source of truth for the ride-hailing flow. The device GPS feeds the
 * backend, and the backend streams back the live city: nearby drivers, incoming
 * requests, the active ride and the account state.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val locationProvider = LocationProvider(application)
    private val session = DeviceSession(application)
    private val backend = AnaneBackend()
    private val placesRepository = PlacesRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        AppUiState(role = if (session.lastRole == "DRIVER") AppRole.DRIVER else AppRole.PASSENGER)
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val registrationDraft = MutableStateFlow(DriverProfile())

    private var locationJob: Job? = null
    private var streamJob: Job? = null
    private var heartbeatJob: Job? = null
    private var placesSearchJob: Job? = null
    private var lastSentLocation: Coordinate? = null

    init {
        if (!backend.isConfigured) {
            _uiState.update {
                it.copy(
                    connection = ConnectionStatus.OFFLINE,
                    lastMessage = "El servicio Añane Go no está configurado en esta compilación"
                )
            }
        } else {
            connectStream()
            startHeartbeat()
        }
        refreshLocationPermission()
    }

    // ------------------------------------------------------------- connection

    /** Keeps the realtime link alive, reconnecting with a short backoff. */
    private fun connectStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            var attempt = 0
            while (isActive) {
                val role = if (_uiState.value.role == AppRole.DRIVER) "DRIVER" else "PASSENGER"
                backend.liveStream(session.userId, role).collect { event ->
                    when (event) {
                        is LiveEvent.Status -> {
                            if (event.status == ConnectionStatus.LIVE) {
                                attempt = 0
                                lastSentLocation = null
                                pushCurrentLocation()
                            }
                            _uiState.update { it.copy(connection = event.status) }
                        }
                        is LiveEvent.Snapshot -> applySnapshot(event.snapshot)
                        is LiveEvent.Notice -> {
                            _uiState.update { it.copy(lastMessage = event.message) }
                        }
                    }
                }
                if (!isActive) return@launch
                attempt += 1
                delay((1_000L * attempt).coerceAtMost(8_000L))
            }
        }
    }

    /** Presence ping so the backend keeps this device listed while idle. */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                backend.pushHeartbeat()
            }
        }
    }

    private fun applySnapshot(snapshot: NetworkSnapshot) {
        val documents = session.readDocuments()
        _uiState.update { state ->
            val activeRide = snapshot.activeRide?.let { SnapshotMapper.ride(it, snapshot.serverTime) }
            // When the passenger's ride leaves the active slot as COMPLETED, surface the payment sheet.
            val finishedId = state.activeRide?.id?.takeIf { it != activeRide?.id }
            val justCompleted = finishedId?.let { id ->
                snapshot.history.firstOrNull {
                    it.id == id && it.status == "COMPLETED" && it.passengerId == session.userId
                }
            }?.let { SnapshotMapper.ride(it, snapshot.serverTime) }
            state.copy(
                paymentDue = justCompleted ?: state.paymentDue,
                connection = ConnectionStatus.LIVE,
                nearbyDrivers = snapshot.drivers.map(SnapshotMapper::driver),
                liveRequests = snapshot.openRides.map { SnapshotMapper.request(it, snapshot.serverTime) },
                offers = snapshot.offers.mapNotNull { SnapshotMapper.offer(it) },
                activeRide = activeRide,
                isRequestingRide = if (activeRide != null) false else state.isRequestingRide,
                rideHistory = snapshot.history.map { SnapshotMapper.ride(it, snapshot.serverTime) },
                passenger = SnapshotMapper.passengerProfile(snapshot.profile),
                driverProfile = SnapshotMapper.driverProfile(snapshot.profile, documents),
                subscription = SnapshotMapper.subscription(snapshot.profile, snapshot.serverTime),
                isDriverOnline = snapshot.profile.isOnline,
                dayStats = state.dayStats.copy(
                    trips = snapshot.todayTrips,
                    earningsSoles = snapshot.todayEarnings
                )
            )
        }
        // Keep the registration form aligned with the server's verdict, and
        // mirror any document photos that are already on file.
        val storedPhotos = snapshot.profile.uploadedPhotos.toSet()
        registrationDraft.update { draft ->
            draft.copy(
                status = SnapshotMapper.verificationStatus(snapshot.profile.driverStatus),
                rejectionReason = snapshot.profile.rejectionReason,
                hasProfilePhoto = draft.hasProfilePhoto || "PROFILE" in storedPhotos,
                hasDniFront = draft.hasDniFront || "DNI_FRONT" in storedPhotos,
                hasDniBack = draft.hasDniBack || "DNI_BACK" in storedPhotos,
                hasVehicleCard = draft.hasVehicleCard || "VEHICLE_CARD" in storedPhotos,
                hasPlatePhoto = draft.hasPlatePhoto || "PLATE" in storedPhotos,
                hasPaymentQr = "PAYMENT_QR" in storedPhotos
            )
        }
    }

    /** Fires a command and surfaces any refusal message from the server. */
    private fun send(action: String, body: JsonObject) {
        viewModelScope.launch {
            val response = backend.command(action, withUser(body))
            response.snapshot?.let(::applySnapshot)
            response.notice?.let { notice ->
                _uiState.update { it.copy(lastMessage = notice) }
            }
        }
    }

    /** Every command carries the identity and the latest GPS fix. */
    private fun withUser(body: JsonObject): JsonObject = buildJsonObject {
        put("userId", JsonPrimitive(session.userId))
        _uiState.value.userLocation?.let { location ->
            put("lat", JsonPrimitive(location.latitude))
            put("lng", JsonPrimitive(location.longitude))
        }
        body.forEach { (key, value) -> put(key, value) }
    }

    // --------------------------------------------------------------- location

    /** Called on launch and whenever the permission dialog returns. */
    fun refreshLocationPermission() {
        if (!locationProvider.hasPermission) {
            _uiState.update { it.copy(locationStatus = LocationStatus.PERMISSION_REQUIRED) }
            return
        }
        startTrackingLocation()
    }

    private fun startTrackingLocation() {
        if (locationJob?.isActive == true) return
        _uiState.update { it.copy(locationStatus = LocationStatus.LOCATING) }

        locationJob = viewModelScope.launch {
            locationProvider.lastKnownLocation()?.let { applyLocation(it) }
            try {
                locationProvider.locationUpdates().collect { applyLocation(it) }
            } catch (error: Exception) {
                _uiState.update { state ->
                    state.copy(
                        locationStatus = if (state.userLocation != null) {
                            LocationStatus.LIVE
                        } else {
                            LocationStatus.UNAVAILABLE
                        },
                        lastMessage = "No pudimos leer tu GPS. Revisa que la ubicación esté activa."
                    )
                }
            }
        }
    }

    private fun applyLocation(coordinate: Coordinate) {
        _uiState.update {
            it.copy(userLocation = coordinate, locationStatus = LocationStatus.LIVE)
        }
        pushCurrentLocation()
    }

    /** Only reports meaningful movement so the socket stays quiet when parked. */
    private fun pushCurrentLocation() {
        val coordinate = _uiState.value.userLocation ?: return
        val previous = lastSentLocation
        if (previous != null && previous.distanceKmTo(coordinate) < 0.008) return
        lastSentLocation = coordinate
        backend.pushLocation(coordinate.latitude, coordinate.longitude)
    }

    // ------------------------------------------------------------------ roles

    fun setRole(role: AppRole) {
        if (_uiState.value.role == role) return
        session.lastRole = role.name
        _uiState.update { it.copy(role = role, liveRequests = emptyList()) }
        backend.pushRole(role.name)
        send("role", buildJsonObject { put("role", JsonPrimitive(role.name)) })
    }

    fun selectService(service: ServiceKind) {
        _uiState.update { it.copy(selectedService = service) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        placesSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 3) {
            _uiState.update { it.copy(placePredictions = emptyList(), isSearchingPlaces = false) }
            return
        }

        placesSearchJob = viewModelScope.launch {
            delay(450) // debounce so we don't fire a request on every keystroke
            _uiState.update { it.copy(isSearchingPlaces = true) }
            val predictions = placesRepository.autocomplete(trimmed, SATIPO_SEARCH_CENTER)
            if (isActive) {
                _uiState.update { it.copy(placePredictions = predictions, isSearchingPlaces = false) }
            }
        }
    }

    /**
     * Resolves a live Google Places suggestion into a real [Place] with
     * coordinates, so it can be requested exactly like a sample destination.
     * Called once, when the passenger actually taps a suggestion.
     */
    suspend fun resolvePlace(prediction: PlacePrediction): Place? {
        val place = placesRepository.resolvePlace(prediction)
        _uiState.update { it.copy(placePredictions = emptyList(), searchQuery = "") }
        return place
    }

    // ------------------------------------------------------------------ rides

    /** Suggested starting price for a destination under the current service. */
    fun suggestedFare(destination: Place): Double = FareRules.suggestedSoles(
        _uiState.value.selectedService,
        destination.name,
        _uiState.value.mapCenter.distanceKmTo(destination.position)
    )

    /** Publishes a ride request with the passenger's own price, payment choice and landmark. */
    fun requestRide(
        destination: Place,
        proposedFare: Double,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        reference: String = ""
    ) {
        val state = _uiState.value
        if (state.activeRide != null) {
            _uiState.update { it.copy(lastMessage = "Ya tienes un viaje en curso") }
            return
        }
        val pickup = state.mapCenter
        val originName = if (state.hasLiveLocation) "Mi ubicación actual" else "Plaza de Armas de Satipo"

        _uiState.update { it.copy(isRequestingRide = true, searchQuery = "") }

        send(
            "request-ride",
            buildJsonObject {
                put("serviceKind", JsonPrimitive(state.selectedService.name))
                put("proposedFare", JsonPrimitive(FareRules.clamp(proposedFare, state.selectedService)))
                put("originName", JsonPrimitive(originName))
                put("originLat", JsonPrimitive(pickup.latitude))
                put("originLng", JsonPrimitive(pickup.longitude))
                put("destName", JsonPrimitive(destination.name))
                put("destDetail", JsonPrimitive(destination.detail))
                put("destLat", JsonPrimitive(destination.position.latitude))
                put("destLng", JsonPrimitive(destination.position.longitude))
                put("paymentMethod", JsonPrimitive(paymentMethod.name))
                put("reference", JsonPrimitive(reference.trim().take(120)))
                put("passengerCount", JsonPrimitive(state.passenger.defaultPassengerCount))
                put(
                    "preferences",
                    JsonArray(state.passenger.defaultPreferences.map { JsonPrimitive(it.name) })
                )
            }
        )
    }

    fun setPassengerCount(count: Int) {
        val ride = _uiState.value.activeRide ?: return
        val safe = count.coerceIn(1, 4)
        _uiState.update { it.copy(activeRide = it.activeRide?.copy(passengerCount = safe)) }
        send(
            "ride-preferences",
            buildJsonObject {
                put("rideId", JsonPrimitive(ride.id))
                put("passengerCount", JsonPrimitive(safe))
                put("preferences", JsonArray(ride.preferences.map { JsonPrimitive(it.name) }))
            }
        )
    }

    fun togglePreference(preference: RidePreference) {
        val ride = _uiState.value.activeRide ?: return
        val next = if (ride.preferences.contains(preference)) {
            ride.preferences - preference
        } else {
            ride.preferences + preference
        }
        _uiState.update { it.copy(activeRide = it.activeRide?.copy(preferences = next)) }
        send(
            "ride-preferences",
            buildJsonObject {
                put("rideId", JsonPrimitive(ride.id))
                put("passengerCount", JsonPrimitive(ride.passengerCount))
                put("preferences", JsonArray(next.map { JsonPrimitive(it.name) }))
            }
        )
    }

    /** Opens the payment sheet again for a trip (e.g. from the ride screen or history). */
    fun showPayment(ride: Ride) {
        _uiState.update { it.copy(paymentDue = ride) }
    }

    fun dismissPayment() {
        _uiState.update { it.copy(paymentDue = null) }
    }

    /** Public URL of a driver's Yape / Plin QR image. */
    fun paymentQrUrl(driverId: String): String =
        backend.verificationPhotoUrl(driverId, VerificationPhoto.PAYMENT_QR.name)

    /** Saves the driver's Yape / Plin number without resending the document review. */
    fun savePayoutPhone(phone: String) {
        val digits = phone.filter { it.isDigit() }.take(9)
        registrationDraft.update { it.copy(payoutPhone = digits) }
        viewModelScope.launch {
            val response = backend.command(
                "payout",
                withUser(buildJsonObject { put("payoutPhone", JsonPrimitive(digits)) })
            )
            response.snapshot?.let(::applySnapshot)
            _uiState.update {
                it.copy(lastMessage = response.notice ?: "Número de Yape / Plin guardado")
            }
        }
    }

    fun advanceRide() {
        val ride = _uiState.value.activeRide ?: return
        send("advance-ride", buildJsonObject { put("rideId", JsonPrimitive(ride.id)) })
    }

    fun cancelRide() {
        val ride = _uiState.value.activeRide ?: return
        _uiState.update { it.copy(activeRide = null, isRequestingRide = false) }
        send("cancel-ride", buildJsonObject { put("rideId", JsonPrimitive(ride.id)) })
    }

    // ----------------------------------------------------------------- driver

    fun toggleDriverOnline() {
        val next = !_uiState.value.isDriverOnline
        send("online", buildJsonObject { put("isOnline", JsonPrimitive(next)) })
    }

    /** The passenger takes one incoming offer; that price becomes the fare. */
    fun acceptOffer(offer: RideOffer) {
        send(
            "accept-offer",
            buildJsonObject {
                put("rideId", JsonPrimitive(offer.rideId))
                put("driverId", JsonPrimitive(offer.driver.id))
            }
        )
    }

    /** The passenger passes on an offer; that driver stops seeing the request. */
    fun rejectOffer(offer: RideOffer) {
        _uiState.update { state ->
            state.copy(offers = state.offers.filterNot { it.driver.id == offer.driver.id })
        }
        send(
            "reject-offer",
            buildJsonObject {
                put("rideId", JsonPrimitive(offer.rideId))
                put("driverId", JsonPrimitive(offer.driver.id))
            }
        )
    }

    fun acceptRequest(requestId: String) {
        send("accept-ride", buildJsonObject { put("rideId", JsonPrimitive(requestId)) })
    }

    /** Sends (or improves) the driver's single counteroffer for an open request. */
    fun offerRide(requestId: String, amount: Double) {
        send(
            "offer-ride",
            buildJsonObject {
                put("rideId", JsonPrimitive(requestId))
                put("amount", JsonPrimitive(amount))
            }
        )
    }

    fun declineRequest(requestId: String) {
        _uiState.update { state ->
            state.copy(liveRequests = state.liveRequests.filterNot { it.id == requestId })
        }
        send("decline-ride", buildJsonObject { put("rideId", JsonPrimitive(requestId)) })
    }

    fun renewSubscription() {
        send("renew-subscription", buildJsonObject { })
    }

    // -------------------------------------------------- driver registration

    val draft: StateFlow<DriverProfile> = registrationDraft.asStateFlow()

    fun startRegistrationDraft() {
        registrationDraft.value = _uiState.value.driverProfile
    }

    fun updateDraft(transform: (DriverProfile) -> DriverProfile) {
        registrationDraft.update(transform)
    }

    /** Preview URL of a document photo already stored by the service. */
    fun verificationPhotoUrl(kind: VerificationPhoto): String =
        backend.verificationPhotoUrl(session.userId, kind.name)

    /**
     * Compresses a just-captured document photo and uploads it so the review
     * team sees the real DNI, tarjeta or placa, not just a ticked box.
     */
    fun uploadPhoto(kind: VerificationPhoto, uri: Uri) {
        if (kind in _uiState.value.uploadingPhotos) return
        _uiState.update { it.copy(uploadingPhotos = it.uploadingPhotos + kind) }

        viewModelScope.launch {
            val context = getApplication<Application>()
            val bytes = withContext(Dispatchers.IO) {
                DocumentPhotos.compressToJpeg(context, uri)
            }
            if (bytes == null) {
                _uiState.update {
                    it.copy(
                        uploadingPhotos = it.uploadingPhotos - kind,
                        lastMessage = "No pudimos leer la foto, intenta de nuevo"
                    )
                }
                return@launch
            }

            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val response = backend.command(
                "verification-photo",
                buildJsonObject {
                    put("kind", JsonPrimitive(kind.name))
                    put("image", JsonPrimitive(encoded))
                    put("mime", JsonPrimitive("image/jpeg"))
                }
            )
            _uiState.update { it.copy(uploadingPhotos = it.uploadingPhotos - kind) }
            response.snapshot?.let(::applySnapshot)
            val message = when {
                response.ok && kind == VerificationPhoto.PAYMENT_QR -> "QR de cobro guardado"
                response.ok -> "${kind.label} enviada para revisión"
                response.notice != null -> response.notice!!
                else -> "No pudimos subir la foto. Revisa tu conexión."
            }
            _uiState.update { it.copy(lastMessage = message) }
        }
    }

    /** Sends the driver's identity to the backend for review. */
    fun submitRegistration(): Boolean {
        val profile = registrationDraft.value
        if (!profile.isComplete) return false

        session.writeDocuments(profile)
        registrationDraft.value = profile.copy(status = VerificationStatus.PENDING)
        _uiState.update {
            it.copy(
                role = AppRole.DRIVER,
                driverProfile = profile.copy(status = VerificationStatus.PENDING),
                lastMessage = "Documentos enviados. Te avisamos al terminar la revisión."
            )
        }
        session.lastRole = AppRole.DRIVER.name
        backend.pushRole(AppRole.DRIVER.name)
        send(
            "submit-verification",
            buildJsonObject {
                put("name", JsonPrimitive(profile.fullName))
                put("dni", JsonPrimitive(profile.dni))
                put("plate", JsonPrimitive(profile.plate))
                put("vehicleType", JsonPrimitive(profile.vehicleType.name))
                put("phone", JsonPrimitive(profile.phone))
                put("vehicleModel", JsonPrimitive(profile.vehicleModel.trim()))
                put("payoutPhone", JsonPrimitive(profile.payoutPhone))
            }
        )
        return true
    }

    /** Saves the passenger's identity so drivers see a verified name. */
    fun savePassengerProfile(name: String, phone: String, dni: String) {
        send(
            "profile",
            buildJsonObject {
                put("name", JsonPrimitive(name))
                put("phone", JsonPrimitive(phone))
                put("dni", JsonPrimitive(dni))
            }
        )
    }

    fun consumeMessage() {
        _uiState.update { it.copy(lastMessage = null) }
    }

    // ------------------------------------------------------- document review

    /** State of the hidden, PIN-protected document review console. */
    data class AdminUiState(
        val isUnlocked: Boolean = false,
        val isLoading: Boolean = false,
        val pending: List<NetworkAdminDriver> = emptyList(),
        val error: String? = null
    )

    private val _adminState = MutableStateFlow(AdminUiState())
    val adminState: StateFlow<AdminUiState> = _adminState.asStateFlow()

    private var adminPin: String? = null

    /** Stores the PIN and loads the pending review queue. */
    fun adminUnlock(pin: String) {
        adminPin = pin
        refreshAdminQueue()
    }

    /** Re-fetches the drivers waiting for a document review. */
    fun refreshAdminQueue() {
        val pin = adminPin ?: return
        _adminState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = backend.adminList(pin)
            _adminState.update {
                if (result.error != null) {
                    it.copy(isLoading = false, isUnlocked = false, error = result.error)
                } else {
                    it.copy(
                        isLoading = false,
                        isUnlocked = true,
                        pending = result.pending,
                        error = null
                    )
                }
            }
        }
    }

    /** Approves or rejects a driver's documents, then refreshes the queue. */
    fun adminDecide(targetUserId: String, approve: Boolean, reason: String) {
        val pin = adminPin ?: return
        viewModelScope.launch {
            val response = backend.adminDecide(pin, targetUserId, approve, reason)
            if (!response.ok) {
                _adminState.update {
                    it.copy(error = response.error ?: response.notice ?: "No se pudo aplicar la decisión")
                }
            } else {
                _adminState.update { it.copy(error = null) }
                refreshAdminQueue()
            }
        }
    }

    /** Preview URL of a stored document photo for the review console. */
    fun adminPhotoUrl(userId: String, kind: String): String =
        backend.verificationPhotoUrl(userId, kind)

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        streamJob?.cancel()
        heartbeatJob?.cancel()
    }

    companion object {
        fun formatSoles(value: Double): String = "S/ ${"%.2f".format(value)}"
        fun formatKm(value: Double): String = when {
            value < 1.0 -> "${(value * 1000).roundToInt()} m"
            value >= 10 -> "${value.roundToInt()} km"
            else -> "${"%.1f".format(value)} km"
        }
    }
}
