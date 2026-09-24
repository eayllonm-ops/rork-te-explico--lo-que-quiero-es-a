package com.rork.ananego.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.ananego.data.model.Ride
import com.rork.ananego.data.model.RideOffer
import com.rork.ananego.data.model.RidePreference
import com.rork.ananego.data.model.RideStatus
import com.rork.ananego.ui.components.FitRouteEffect
import com.rork.ananego.ui.components.InitialsAvatar
import com.rork.ananego.ui.components.JungleCard
import com.rork.ananego.ui.components.MapMarker
import com.rork.ananego.ui.components.RatingRow
import com.rork.ananego.ui.components.SatipoMap
import com.rork.ananego.ui.components.StaticLocationDot
import com.rork.ananego.ui.components.VehiclePin
import com.rork.ananego.ui.components.icon
import com.rork.ananego.ui.components.rememberSatipoCamera
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.GoldDeep
import com.rork.ananego.ui.theme.JungleCanvas
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.JungleSurface
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

private fun RidePreference.icon(): ImageVector = when (this) {
    RidePreference.WHEELCHAIR -> Icons.Filled.Accessible
    RidePreference.LUGGAGE -> Icons.Filled.Luggage
    RidePreference.ELDERLY -> Icons.Filled.Elderly
    RidePreference.CHILD_SEAT -> Icons.Filled.ChildCare
    RidePreference.PET -> Icons.Filled.Pets
}

/** Shown for the moment between sending the request and the server confirming it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishingRideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = JungleCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Publicando tu viaje", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JungleCanvas,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = GoldAccent, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Enviando tu solicitud a los conductores de Satipo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * Detail screen tracking the assigned driver, with passenger preferences
 * (count, accessibility, luggage) that can be adjusted at any moment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideTrackingScreen(
    ride: Ride,
    offers: List<RideOffer>,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onAdvance: () -> Unit,
    onAcceptOffer: (RideOffer) -> Unit,
    onRejectOffer: (RideOffer) -> Unit,
    onPassengerCountChange: (Int) -> Unit,
    onTogglePreference: (RidePreference) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = JungleCanvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (ride.status) {
                            RideStatus.SEARCHING -> "Buscando tu conductor"
                            RideStatus.ACCEPTED -> "Tu conductor va en camino"
                            RideStatus.ARRIVED -> "Tu conductor llegó"
                            RideStatus.ON_TRIP -> "En viaje a ${ride.destination.name}"
                            else -> ride.status.label
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JungleCanvas,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            BottomActions(
                ride = ride,
                onCancel = onCancel,
                onAdvance = onAdvance
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RouteMap(ride = ride)

            if (ride.driver == null) {
                SearchingCard(ride = ride, modifier = Modifier.padding(horizontal = 16.dp))

                OfferList(
                    offers = offers,
                    onAccept = onAcceptOffer,
                    onReject = onRejectOffer,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                DriverCard(ride = ride, modifier = Modifier.padding(horizontal = 16.dp))
            }

            TripSummary(ride = ride, modifier = Modifier.padding(horizontal = 16.dp))

            PreferencesBlock(
                ride = ride,
                onPassengerCountChange = onPassengerCountChange,
                onTogglePreference = onTogglePreference,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RouteMap(ride: Ride) {
    val pickup = ride.origin.position
    val destination = ride.destination.position
    val driver = ride.driver
    val isOnTrip = ride.status == RideStatus.ON_TRIP
    val routeFrom = if (isOnTrip) pickup else driver?.position ?: pickup
    val routeTo = if (isOnTrip) destination else pickup

    val cameraPositionState = rememberSatipoCamera(pickup)
    var isMapLoaded by remember { mutableStateOf(false) }

    FitRouteEffect(
        cameraPositionState = cameraPositionState,
        isMapLoaded = isMapLoaded,
        from = routeFrom,
        to = routeTo
    )

    SatipoMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        cameraPositionState = cameraPositionState,
        routeFrom = routeFrom,
        routeTo = routeTo,
        onMapLoaded = { isMapLoaded = true }
    ) {
        driver?.let { assigned ->
            MapMarker(
                position = assigned.position,
                assigned.id,
                assigned.etaMinutes,
                title = "${assigned.name} · llega en ${assigned.etaMinutes} min",
                zIndex = 5f
            ) {
                VehiclePin(vehicleType = assigned.vehicleType)
            }
        }
        MapMarker(position = pickup, "pickup", zIndex = 4f) {
            StaticLocationDot()
        }
        MapMarker(position = destination, ride.destination.name, zIndex = 4f) {
            Surface(shape = RoundedCornerShape(10.dp), color = GoldDeep) {
                Text(
                    text = ride.destination.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = JungleDeep,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchingCard(ride: Ride, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "searching")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = GoldAccent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Esperando ofertas",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tu precio: ${AppViewModel.formatSoles(ride.fareSoles)}. " +
                        "Los conductores pueden aceptarlo u ofrecerte otro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = alpha)
                )
            }
        }
    }
}

/** Live counteroffers for the passenger's open price, ready to accept. */
@Composable
private fun OfferList(
    offers: List<RideOffer>,
    onAccept: (RideOffer) -> Unit,
    onReject: (RideOffer) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (offers.isEmpty()) {
            Text(
                text = "Todavía no hay ofertas. Avisamos a los conductores cerca de ti…",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        offers.forEach { offer ->
            OfferCard(offer = offer, onAccept = onAccept, onReject = onReject)
        }
    }
}

@Composable
private fun OfferCard(
    offer: RideOffer,
    onAccept: (RideOffer) -> Unit,
    onReject: (RideOffer) -> Unit,
    modifier: Modifier = Modifier
) {
    val driver = offer.driver
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(initials = driver.initials, size = 48.dp, verified = driver.isVerified)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = driver.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (driver.isSimulated) {
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(50), color = JungleOutline) {
                                Text(
                                    text = "demo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${driver.vehicleType.label} · ${driver.plate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingRow(rating = driver.rating)
                        Text(
                            text = " · a ${driver.etaMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Text(
                    text = AppViewModel.formatSoles(offer.amountSoles),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = GoldAccent
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onReject(offer) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, JungleOutline)
                ) {
                    Text("Rechazar", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = { onAccept(offer) },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldAccent,
                        contentColor = JungleDeep
                    )
                ) {
                    Text(
                        text = "Aceptar ${AppViewModel.formatSoles(offer.amountSoles)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverCard(ride: Ride, modifier: Modifier = Modifier) {
    val driver = ride.driver ?: return
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(initials = driver.initials, size = 64.dp, verified = driver.isVerified)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = driver.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (driver.isSimulated) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(50), color = JungleOutline) {
                            Text(
                                text = "demo",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${driver.vehicleType.label} · ${driver.plate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingRow(rating = driver.rating)
                    Text(
                        text = " · Llega en ${driver.etaMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldAccent
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(GoldAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = driver.vehicleType.icon(),
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun TripSummary(ride: Ride, modifier: Modifier = Modifier) {
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TripPoint(label = "Recojo", value = ride.origin.name, color = SuccessGreen)
            TripPoint(label = "Destino", value = ride.destination.name, color = GoldDeep)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (ride.status == RideStatus.SEARCHING) "Tu precio propuesto" else "Tarifa acordada",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = AppViewModel.formatSoles(ride.fareSoles),
                    style = MaterialTheme.typography.headlineSmall,
                    color = GoldAccent
                )
            }
        }
    }
}

@Composable
private fun TripPoint(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PreferencesBlock(
    ride: Ride,
    onPassengerCountChange: (Int) -> Unit,
    onTogglePreference: (RidePreference) -> Unit,
    modifier: Modifier = Modifier
) {
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(
                    text = "Tu viaje",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Confirma tus preferencias para un mejor servicio",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (ride.passengerCount == 1) "1 pasajero" else "${ride.passengerCount} pasajeros",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepperButton(
                        icon = Icons.Filled.Remove,
                        description = "Quitar pasajero",
                        enabled = ride.passengerCount > 1,
                        onClick = { onPassengerCountChange(ride.passengerCount - 1) }
                    )
                    Spacer(Modifier.width(12.dp))
                    StepperButton(
                        icon = Icons.Filled.Add,
                        description = "Agregar pasajero",
                        enabled = ride.passengerCount < 4,
                        onClick = { onPassengerCountChange(ride.passengerCount + 1) }
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RidePreference.entries.forEach { preference ->
                    val selected = ride.preferences.contains(preference)
                    FilterChip(
                        selected = selected,
                        onClick = { onTogglePreference(preference) },
                        label = { Text(preference.label, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                imageVector = preference.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (selected) GoldAccent else JungleOutline),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = JungleSurface,
                            labelColor = TextSecondary,
                            iconColor = TextSecondary,
                            selectedContainerColor = GoldAccent,
                            selectedLabelColor = JungleDeep,
                            selectedLeadingIconColor = JungleDeep
                        )
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Puedes modificar estas opciones en cualquier momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (enabled) JungleOutline else JungleOutline.copy(alpha = 0.4f),
        modifier = Modifier.size(38.dp)
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (enabled) Color.White else TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BottomActions(
    ride: Ride,
    onCancel: () -> Unit,
    onAdvance: () -> Unit
) {
    Surface(color = JungleCanvas, tonalElevation = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ride.status == RideStatus.ARRIVED || ride.status == RideStatus.ON_TRIP) {
                Button(
                    onClick = onAdvance,
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
                        text = if (ride.status == RideStatus.ARRIVED) "Ya subí al vehículo" else "Terminar viaje",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, JungleOutline),
                    enabled = ride.driver != null
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Llamar", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = GoldAccent
                    ),
                    border = BorderStroke(1.dp, GoldDeep)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancelar viaje", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
