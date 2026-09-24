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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.ananego.data.model.DriverDayStats
import com.rork.ananego.data.model.RidePreference
import com.rork.ananego.data.model.RideRequest
import com.rork.ananego.data.model.Subscription
import com.rork.ananego.data.model.VerificationStatus
import com.rork.ananego.ui.components.EmptyState
import com.rork.ananego.ui.components.InitialsAvatar
import com.rork.ananego.ui.components.JungleCard
import com.rork.ananego.ui.components.MiniBars
import com.rork.ananego.ui.components.ProgressRing
import com.rork.ananego.ui.components.SectionHeader
import com.rork.ananego.ui.components.TagPill
import com.rork.ananego.ui.components.clickableCard
import com.rork.ananego.ui.state.AppUiState
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.DangerRed
import com.rork.ananego.ui.theme.GoldDeep
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.JungleSurface
import com.rork.ananego.ui.theme.JungleSurfaceHigh
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

/**
 * Driver dashboard: weekly subscription status (the business model), live
 * incoming ride requests and today's metrics, with an availability switch.
 */
@Composable
fun DriverDashboardScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onToggleOnline: () -> Unit,
    onOpenRequest: (RideRequest) -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenRegistration: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 110.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.driverProfile.status != VerificationStatus.VERIFIED) {
            item {
                VerificationBanner(
                    status = state.driverProfile.status,
                    onClick = onOpenRegistration,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        item {
            SubscriptionCard(
                subscription = state.subscription,
                onClick = onOpenSubscription,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item {
            SectionHeader(
                title = "Solicitudes de viaje",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = { LivePill(isOnline = state.isDriverOnline) }
            )
        }

        if (!state.isDriverOnline) {
            item {
                JungleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.PauseCircle,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Estás desconectado. Ponte disponible para recibir solicitudes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else if (state.driverRequests.isEmpty()) {
            item {
                EmptyState(
                    title = "Sin solicitudes por ahora",
                    subtitle = "Mantente conectado, las solicitudes llegan en tiempo real.",
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Inbox,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                )
            }
        } else {
            items(state.driverRequests, key = { it.id }) { request ->
                RequestRow(
                    request = request,
                    onClick = { onOpenRequest(request) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        item {
            SectionHeader(
                title = "Tus métricas de hoy",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    TextButton(onClick = onOpenSubscription) {
                        Text("Ver detalles", color = GoldAccent, style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }

        item {
            StatsCard(stats = state.dayStats, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** Bottom action bar for going online; rendered above the tab bar by the host. */
@Composable
fun DriverAvailabilityBar(
    isOnline: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Button(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isOnline) SuccessGreen else GoldAccent,
                contentColor = JungleDeep,
                disabledContainerColor = JungleSurfaceHigh,
                disabledContentColor = TextSecondary
            )
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Filled.PauseCircle else Icons.Filled.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isOnline) "Estoy en pausa" else "Estoy disponible",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VerificationBanner(
    status: VerificationStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        VerificationStatus.PENDING -> GoldAccent
        VerificationStatus.REJECTED -> DangerRed
        else -> GoldDeep
    }
    JungleCard(
        modifier = modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .clickableCard(onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (status) {
                        VerificationStatus.PENDING -> "Documentos en revisión"
                        VerificationStatus.REJECTED -> "Verificación rechazada"
                        else -> "Completa tu registro de conductor"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
                Text(
                    text = when (status) {
                        VerificationStatus.PENDING -> "Te avisamos en menos de 24 horas."
                        VerificationStatus.REJECTED -> "Vuelve a subir tus documentos."
                        else -> "Sube tu DNI y tarjeta de propiedad para recibir viajes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = color
            )
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    JungleCard(modifier = modifier.fillMaxWidth(), color = JungleSurface) {
        Row(
            modifier = Modifier
                .clickableCard(onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressRing(
                progress = subscription.progress,
                ringColor = if (subscription.isActive) GoldAccent else DangerRed,
                modifier = Modifier.size(128.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${subscription.daysRemaining}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "días\nrestantes",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (subscription.isActive) "Suscripción activa" else "Suscripción vencida",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "S/ ${"%.2f".format(subscription.paidThisWeekSoles)} pagado esta semana",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SuccessGreen.copy(alpha = 0.18f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Conduce sin comisiones",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePill(isOnline: Boolean) {
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "pulse"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (isOnline) SuccessGreen else TextSecondary)
                .alpha(if (isOnline) pulse else 1f)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (isOnline) "En tiempo real" else "Desconectado",
            style = MaterialTheme.typography.labelMedium,
            color = if (isOnline) SuccessGreen else TextSecondary
        )
    }
}

@Composable
private fun RequestRow(
    request: RideRequest,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickableCard(onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(
                initials = request.passengerInitials,
                size = 52.dp,
                accent = GoldDeep,
                verified = request.passengerVerified
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${request.passengerName} solicita viaje",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${request.originName} → ${request.destinationName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Hace ${request.minutesAgo} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (request.preferences.contains(RidePreference.WHEELCHAIR)) {
                        TagPill(
                            text = "Accesible",
                            color = GoldAccent,
                            leading = {
                                Icon(
                                    Icons.Filled.Accessible,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = AppViewModel.formatSoles(request.fareSoles),
                    style = MaterialTheme.typography.titleLarge,
                    color = GoldDeep
                )
                if (request.myOfferSoles != null) {
                    TagPill(
                        text = "Oferta enviada: ${AppViewModel.formatSoles(request.myOfferSoles!!)}",
                        color = GoldAccent
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun StatsCard(stats: DriverDayStats, modifier: Modifier = Modifier) {
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                MiniBars(values = listOf(0.4f, 0.7f, 0.55f, 1f), color = SuccessGreen)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${stats.trips}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("viajes hoy", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(JungleOutline)
            )
            Row(modifier = Modifier.weight(1f).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = GoldDeep,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = AppViewModel.formatSoles(stats.earningsSoles),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("ganado", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TagPill(
                text = "${stats.onlineMinutes / 60}h ${stats.onlineMinutes % 60}m en línea",
                color = TextSecondary
            )
            TagPill(
                text = "${stats.acceptanceRate}% aceptación",
                color = SuccessGreen,
                leading = {
                    Icon(
                        Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(12.dp)
                    )
                }
            )
        }
    }
}
