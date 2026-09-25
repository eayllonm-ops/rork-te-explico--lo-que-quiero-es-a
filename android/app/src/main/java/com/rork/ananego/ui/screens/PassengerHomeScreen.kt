package com.rork.ananego.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rork.ananego.data.model.Coordinate
import com.rork.ananego.data.model.Driver
import com.rork.ananego.data.model.FareRules
import com.rork.ananego.data.model.LocationStatus
import com.rork.ananego.data.model.Place
import com.rork.ananego.data.model.PlacePrediction
import com.rork.ananego.data.model.ServiceKind
import com.rork.ananego.data.model.VehicleType
import com.rork.ananego.ui.components.CITY_ZOOM
import com.rork.ananego.ui.components.DriverRow
import com.rork.ananego.ui.components.FollowUserEffect
import com.rork.ananego.ui.components.JungleCard
import com.rork.ananego.ui.components.MapMarker
import com.rork.ananego.ui.components.SatipoMap
import com.rork.ananego.ui.components.SectionHeader
import com.rork.ananego.ui.components.StaticLocationDot
import com.rork.ananego.ui.components.VehiclePin
import com.rork.ananego.ui.components.clickableCard
import com.rork.ananego.ui.components.icon
import com.rork.ananego.ui.components.recenterOn
import com.rork.ananego.ui.components.rememberSatipoCamera
import com.rork.ananego.ui.state.AppUiState
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.GoldDeep
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.JungleSurface
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Passenger home: stylized Satipo map, destination search, service choice
 * between local mototaxi and intercity car, and nearby verified drivers.
 */
@Composable
fun PassengerHomeScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onSelectService: (ServiceKind) -> Unit,
    onSearchChange: (String) -> Unit,
    onResolvePrediction: suspend (PlacePrediction) -> Place?,
    onRequestRide: (Place, Double) -> Unit,
    onOpenActiveRide: () -> Unit,
    onOpenDriver: (Driver) -> Unit,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearching by remember { mutableStateOf(false) }
    var pendingPlace by remember { mutableStateOf<Place?>(null) }
    val predictionScope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MapHeader(
                state = state,
                onOpenActiveRide = onOpenActiveRide,
                onRequestLocationPermission = onRequestLocationPermission
            )
        }

        item {
            SearchField(
                query = state.searchQuery,
                isSearching = isSearching,
                onQueryChange = {
                    onSearchChange(it)
                    isSearching = true
                },
                onFocusChange = { isSearching = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item {
            AnimatedVisibility(
                visible = isSearching,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                DestinationSuggestions(
                    places = state.destinationOptions,
                    onPick = { place ->
                        isSearching = false
                        onSearchChange("")
                        pendingPlace = place
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        item {
            AnimatedVisibility(
                visible = isSearching && state.searchQuery.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LivePlaceSuggestions(
                    predictions = state.placePredictions,
                    isLoading = state.isSearchingPlaces,
                    onPick = { prediction ->
                        predictionScope.launch {
                            val resolved = onResolvePrediction(prediction)
                            if (resolved != null) {
                                isSearching = false
                                onSearchChange("")
                                pendingPlace = resolved
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        item {
            ServiceChoiceRow(
                selected = state.selectedService,
                onSelect = onSelectService,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item {
            SectionHeader(
                title = "Conductores cercanos",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    TextButton(onClick = { isSearching = true }) {
                        Text("Pedir viaje", color = GoldAccent, style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }

        items(state.filteredDrivers, key = { it.id }) { driver ->
            DriverRow(
                driver = driver,
                onClick = { onOpenDriver(driver) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item {
            SafetyNote(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }

    pendingPlace?.let { place ->
        val suggested = FareRules.suggestedSoles(
            state.selectedService,
            place.name,
            state.mapCenter.distanceKmTo(place.position)
        )
        var amount by remember(place) { mutableStateOf(suggested) }
        PriceProposalSheet(
            place = place,
            amount = amount,
            suggested = suggested,
            floor = FareRules.floorSoles(state.selectedService),
            onAdjust = { delta -> amount = FareRules.clamp(amount + delta, state.selectedService) },
            onConfirm = {
                pendingPlace = null
                onRequestRide(place, amount)
            },
            onDismiss = { pendingPlace = null }
        )
    }
}

@Composable
private fun MapHeader(
    state: AppUiState,
    onOpenActiveRide: () -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    val center = state.mapCenter
    val cameraPositionState = rememberSatipoCamera(center)
    var isMapLoaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FollowUserEffect(
        cameraPositionState = cameraPositionState,
        isMapLoaded = isMapLoaded,
        location = state.userLocation
    )

    Box {
        SatipoMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            cameraPositionState = cameraPositionState,
            showMyLocation = state.hasLiveLocation,
            onMapLoaded = { isMapLoaded = true }
        ) {
            state.filteredDrivers.forEach { driver ->
                MapMarker(
                    position = driver.position,
                    driver.id,
                    driver.vehicleType,
                    title = "${driver.name} · ${driver.plate}"
                ) {
                    VehiclePin(vehicleType = driver.vehicleType)
                }
            }
            if (!state.hasLiveLocation) {
                MapMarker(position = center, "fallback-pickup") {
                    StaticLocationDot()
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = JungleDeep.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(48.dp)
        ) {
            IconButton(
                onClick = {
                    if (state.locationStatus == LocationStatus.PERMISSION_REQUIRED) {
                        onRequestLocationPermission()
                    } else {
                        scope.launch {
                            cameraPositionState.recenterOn(center.toLatLng(), CITY_ZOOM)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "Centrar en mi ubicación",
                    tint = if (state.hasLiveLocation) SuccessGreen else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        LocationStatusChip(
            status = state.locationStatus,
            onRequestPermission = onRequestLocationPermission,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        if (state.activeRide != null) {
            JungleCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                color = GoldAccent
            ) {
                Row(
                    modifier = Modifier
                        .clickableCard(onOpenActiveRide)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tienes un viaje en curso",
                        style = MaterialTheme.typography.titleMedium,
                        color = JungleDeep,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = JungleDeep
                    )
                }
            }
        }
    }
}

/** Small chip explaining where the map position comes from. */
@Composable
private fun LocationStatusChip(
    status: LocationStatus,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (status) {
        LocationStatus.LIVE -> "GPS en vivo" to SuccessGreen
        LocationStatus.LOCATING -> "Buscando tu señal GPS…" to GoldAccent
        LocationStatus.PERMISSION_REQUIRED -> "Activar mi ubicación" to GoldDeep
        LocationStatus.UNAVAILABLE -> "GPS sin señal · Satipo centro" to GoldDeep
        LocationStatus.IDLE -> return
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = JungleDeep.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .clickableCard {
                    if (status == LocationStatus.PERMISSION_REQUIRED) onRequestPermission()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    JungleCard(
        modifier = modifier.fillMaxWidth(),
        color = JungleSurface,
        border = BorderStroke(1.dp, if (isSearching) GoldAccent else JungleOutline)
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("¿A dónde vas?", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary)
            },
            trailingIcon = {
                if (isSearching || query.isNotEmpty()) {
                    IconButton(onClick = {
                        onQueryChange("")
                        onFocusChange(false)
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda", tint = TextSecondary)
                    }
                } else {
                    IconButton(onClick = { onFocusChange(true) }) {
                        Icon(Icons.Filled.Place, contentDescription = "Elegir destino", tint = GoldAccent)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = GoldAccent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
private fun DestinationSuggestions(
    places: List<Place>,
    onPick: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.heightIn(max = 280.dp)) {
            places.forEach { place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableCard { onPick(place) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GoldAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Place,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = place.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (places.isEmpty()) {
                Text(
                    text = "No encontramos ese destino",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/**
 * Real streets and points of interest from Google Places, shown below the
 * fixed [DestinationSuggestions] once the passenger has typed at least three
 * characters. Resolves to full coordinates only when the passenger taps one.
 */
@Composable
private fun LivePlaceSuggestions(
    predictions: List<PlacePrediction>,
    isLoading: Boolean,
    onPick: (PlacePrediction) -> Unit,
    modifier: Modifier = Modifier
) {
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.heightIn(max = 280.dp)) {
            if (isLoading && predictions.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = GoldAccent
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Buscando direcciones…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            } else {
                predictions.forEach { prediction ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableCard { onPick(prediction) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoldAccent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = prediction.primaryText,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (prediction.secondaryText.isNotBlank()) {
                                Text(
                                    text = prediction.secondaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (predictions.isEmpty()) {
                    Text(
                        text = "No encontramos esa dirección",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceChoiceRow(
    selected: ServiceKind,
    onSelect: (ServiceKind) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ServiceCard(
            kind = ServiceKind.LOCAL_MOTOTAXI,
            vehicleType = VehicleType.MOTOTAXI,
            isSelected = selected == ServiceKind.LOCAL_MOTOTAXI,
            onClick = { onSelect(ServiceKind.LOCAL_MOTOTAXI) },
            modifier = Modifier.weight(1f)
        )
        ServiceCard(
            kind = ServiceKind.INTERCITY,
            vehicleType = VehicleType.INTERCITY_CAR,
            isSelected = selected == ServiceKind.INTERCITY,
            onClick = { onSelect(ServiceKind.INTERCITY) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ServiceCard(
    kind: ServiceKind,
    vehicleType: VehicleType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (isSelected) GoldAccent else JungleSurface,
        animationSpec = tween(250),
        label = "serviceBg"
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        animationSpec = tween(250),
        label = "serviceElevation"
    )
    val onContainer = if (isSelected) JungleDeep else MaterialTheme.colorScheme.onSurface
    val subColor = if (isSelected) JungleDeep.copy(alpha = 0.75f) else TextSecondary

    Surface(
        modifier = modifier.heightIn(min = 124.dp),
        shape = RoundedCornerShape(20.dp),
        color = container,
        shadowElevation = elevation,
        border = BorderStroke(1.dp, if (isSelected) GoldAccent else JungleOutline)
    ) {
        Column(
            modifier = Modifier
                .clickableCard(onClick)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = vehicleType.icon(),
                contentDescription = null,
                tint = if (isSelected) JungleDeep else GoldDeep,
                modifier = Modifier.size(30.dp)
            )
            Text(
                text = kind.title,
                style = MaterialTheme.typography.titleMedium,
                color = onContainer
            )
            Text(
                text = kind.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subColor
            )
        }
    }
}

@Composable
private fun SafetyNote(modifier: Modifier = Modifier) {
    JungleCard(modifier = modifier.fillMaxWidth(), color = JungleSurface) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Conductores con DNI y tarjeta de propiedad verificados, y ubicación GPS en tiempo real.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

/** Sheet where the passenger sets their own price before requesting the ride. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceProposalSheet(
    place: Place,
    amount: Double,
    suggested: Double,
    floor: Double,
    onAdjust: (Double) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = JungleSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Propón tu precio",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${place.name} · ${place.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(shape = CircleShape, color = JungleOutline, modifier = Modifier.size(52.dp)) {
                    IconButton(onClick = { onAdjust(-FareRules.STEP_SOLES) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Bajar S/ 0.50", tint = GoldAccent)
                    }
                }
                Text(
                    text = AppViewModel.formatSoles(amount),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldAccent
                )
                Surface(shape = CircleShape, color = JungleOutline, modifier = Modifier.size(52.dp)) {
                    IconButton(onClick = { onAdjust(FareRules.STEP_SOLES) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Subir S/ 0.50", tint = GoldAccent)
                    }
                }
            }

            Text(
                text = "Tarifa sugerida ${AppViewModel.formatSoles(suggested)} · Mínimo ${AppViewModel.formatSoles(floor)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = JungleDeep
                )
            ) {
                Text(
                    text = "Pedir viaje con ${AppViewModel.formatSoles(amount)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
