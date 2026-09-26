package com.rork.ananego.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.ananego.data.model.FareRules
import com.rork.ananego.data.model.RideRequest
import com.rork.ananego.ui.components.FitRouteEffect
import com.rork.ananego.ui.components.InitialsAvatar
import com.rork.ananego.ui.components.JungleCard
import com.rork.ananego.ui.components.MapMarker
import com.rork.ananego.ui.components.RatingRow
import com.rork.ananego.ui.components.SatipoMap
import com.rork.ananego.ui.components.StaticLocationDot
import com.rork.ananego.ui.components.TagPill
import com.rork.ananego.ui.components.rememberSatipoCamera
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.GoldDeep
import com.rork.ananego.ui.theme.JungleCanvas
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

/** Full detail of an incoming passenger request, with accept/decline actions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RequestDetailScreen(
    request: RideRequest,
    onBack: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCounterOffer: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var isOffering by remember { mutableStateOf(request.myOfferSoles == null) }
    var offerAmount by remember {
        mutableStateOf(
            FareRules.clamp(
                (request.myOfferSoles ?: request.fareSoles) + FareRules.STEP_SOLES,
                request.serviceKind
            )
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = JungleCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Solicitud de viaje", style = MaterialTheme.typography.titleMedium) },
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
            Surface(color = JungleCanvas) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, JungleOutline)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Rechazar", color = MaterialTheme.colorScheme.onBackground)
                    }
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = JungleDeep
                        )
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Aceptar ${AppViewModel.formatSoles(request.fareSoles)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val cameraPositionState = rememberSatipoCamera(request.origin)
            var isMapLoaded by remember { mutableStateOf(false) }

            FitRouteEffect(
                cameraPositionState = cameraPositionState,
                isMapLoaded = isMapLoaded,
                from = request.origin,
                to = request.destination,
                paddingPx = 110
            )

            SatipoMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                cameraPositionState = cameraPositionState,
                routeFrom = request.origin,
                routeTo = request.destination,
                onMapLoaded = { isMapLoaded = true }
            ) {
                MapMarker(
                    position = request.origin,
                    "origin",
                    title = request.originName
                ) {
                    StaticLocationDot()
                }
                MapMarker(
                    position = request.destination,
                    request.destinationName,
                    title = request.destinationName
                ) {
                    Surface(shape = RoundedCornerShape(10.dp), color = GoldDeep) {
                        Text(
                            text = request.destinationName,
                            style = MaterialTheme.typography.labelSmall,
                            color = JungleDeep,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            JungleCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialsAvatar(
                            initials = request.passengerInitials,
                            size = 60.dp,
                            accent = GoldDeep,
                            verified = request.passengerVerified
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = request.passengerName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RatingRow(rating = request.passengerRating)
                            if (request.passengerVerified) {
                                TagPill(
                                    text = "Identidad verificada",
                                    color = SuccessGreen,
                                    leading = {
                                        Icon(
                                            Icons.Filled.Verified,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = 0.dp)
                    ) {
                        Surface(color = JungleOutline, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                    }

                    InfoRow(label = "Recojo", value = request.originName, dotColor = SuccessGreen)
                    InfoRow(label = "Destino", value = request.destinationName, dotColor = GoldDeep)
                    if (request.reference.isNotBlank()) {
                        InfoRow(label = "Referencia", value = request.reference, dotColor = TextSecondary)
                    }
                    InfoRow(label = "Pago", value = request.paymentMethod.label, dotColor = GoldAccent)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Distancia", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                text = AppViewModel.formatKm(request.distanceKm),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column {
                            Text("Solicitado", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                text = "Hace ${request.minutesAgo} min",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Pide el pasajero", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                text = AppViewModel.formatSoles(request.fareSoles),
                                style = MaterialTheme.typography.titleLarge,
                                color = GoldDeep
                            )
                        }
                    }
                }
            }

            JungleCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Preferencias del pasajero",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TagPill(
                            text = if (request.passengerCount == 1) "1 pasajero" else "${request.passengerCount} pasajeros",
                            color = GoldAccent,
                            leading = {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        )
                        request.preferences.forEach { preference ->
                            TagPill(text = preference.label, color = SuccessGreen)
                        }
                        if (request.preferences.isEmpty()) {
                            Text(
                                text = "Sin requerimientos especiales",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            OfferCard(
                request = request,
                isOffering = isOffering,
                offerAmount = offerAmount,
                onToggleOffering = { isOffering = !isOffering },
                onAdjust = { delta -> offerAmount = FareRules.clamp(offerAmount + delta, request.serviceKind) },
                onSend = {
                    isOffering = false
                    onCounterOffer(offerAmount)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Counteroffer block: one live offer per driver, improvable while open. */
@Composable
private fun OfferCard(
    request: RideRequest,
    isOffering: Boolean,
    offerAmount: Double,
    onToggleOffering: () -> Unit,
    onAdjust: (Double) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    JungleCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tu oferta",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (request.myOfferSoles != null && !isOffering) {
                Surface(shape = RoundedCornerShape(12.dp), color = GoldAccent.copy(alpha = 0.14f)) {
                    Text(
                        text = "Oferta enviada: ${AppViewModel.formatSoles(request.myOfferSoles)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Text(
                    text = "Puedes mejorarla mientras el viaje siga abierto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedButton(
                    onClick = onToggleOffering,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GoldDeep)
                ) {
                    Text("Mejorar oferta", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(shape = CircleShape, color = JungleOutline, modifier = Modifier.size(44.dp)) {
                        IconButton(onClick = { onAdjust(-FareRules.STEP_SOLES) }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Bajar S/ 0.50", tint = GoldAccent)
                        }
                    }
                    Text(
                        text = AppViewModel.formatSoles(offerAmount),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent
                    )
                    Surface(shape = CircleShape, color = JungleOutline, modifier = Modifier.size(44.dp)) {
                        IconButton(onClick = { onAdjust(FareRules.STEP_SOLES) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Subir S/ 0.50", tint = GoldAccent)
                        }
                    }
                }
                Text(
                    text = "Pide el pasajero ${AppViewModel.formatSoles(request.fareSoles)} · mínimo ${AppViewModel.formatSoles(FareRules.floorSoles(request.serviceKind))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Button(
                    onClick = onSend,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldAccent,
                        contentColor = JungleDeep
                    )
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (request.myOfferSoles == null) {
                            "Ofrecer ${AppViewModel.formatSoles(offerAmount)}"
                        } else {
                            "Actualizar a ${AppViewModel.formatSoles(offerAmount)}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = dotColor, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
