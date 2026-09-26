package com.rork.ananego.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rork.ananego.data.model.Ride
import com.rork.ananego.data.model.RideStatus
import com.rork.ananego.ui.components.EmptyState
import com.rork.ananego.ui.components.JungleCard
import com.rork.ananego.ui.components.SectionHeader
import com.rork.ananego.ui.components.TagPill
import com.rork.ananego.ui.components.clickableCard
import com.rork.ananego.ui.state.AppUiState
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.DangerRed
import com.rork.ananego.ui.theme.GoldDeep
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

/** Passenger trips tab: current ride plus history. */
@Composable
fun TripsScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onOpenActiveRide: () -> Unit,
    onOpenPayment: (Ride) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        state.activeRide?.let { ride ->
            item {
                SectionHeader(title = "Viaje en curso", modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                JungleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = GoldAccent
                ) {
                    Row(
                        modifier = Modifier
                            .clickableCard(onOpenActiveRide)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Route,
                            contentDescription = null,
                            tint = JungleDeep,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ride.destination.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = JungleDeep
                            )
                            Text(
                                text = ride.status.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = JungleDeep.copy(alpha = 0.75f)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = JungleDeep
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Historial", modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (state.rideHistory.isEmpty()) {
            item {
                EmptyState(
                    title = "Aún no tienes viajes",
                    subtitle = "Cuando pidas tu primer mototaxi o auto interprovincial, aparecerá aquí.",
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                )
            }
        }

        items(state.rideHistory, key = { it.id }) { ride ->
            HistoryRow(
                ride = ride,
                onOpenPayment = { onOpenPayment(ride) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun HistoryRow(ride: Ride, onOpenPayment: () -> Unit, modifier: Modifier = Modifier) {
    // Completed trips reopen the pay sheet (QR / number or cash amount).
    val canPay = ride.status == RideStatus.COMPLETED && ride.driver != null
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .then(if (canPay) Modifier.clickableCard(onOpenPayment) else Modifier)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = ride.destination.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${ride.origin.name} → ${ride.destination.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagPill(
                        text = ride.status.label,
                        color = if (ride.status == RideStatus.CANCELLED) DangerRed else SuccessGreen
                    )
                    TagPill(text = ride.paymentMethod.label, color = GoldAccent)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = AppViewModel.formatSoles(ride.fareSoles),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (ride.status == RideStatus.CANCELLED) TextSecondary else GoldDeep
                )
                Text(
                    text = ride.createdAtLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
