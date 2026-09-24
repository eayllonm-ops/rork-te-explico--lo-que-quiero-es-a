package com.rork.ananego.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rork.ananego.data.model.Driver
import com.rork.ananego.data.model.VehicleType
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

fun VehicleType.icon(): ImageVector = when (this) {
    VehicleType.MOTOTAXI -> Icons.Filled.ElectricRickshaw
    VehicleType.INTERCITY_CAR -> Icons.Filled.DirectionsCar
}

/** Nearby-driver row used on the passenger home screen. */
@Composable
fun DriverRow(
    driver: Driver,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    JungleCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableCard(onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(
                initials = driver.initials,
                size = 52.dp,
                verified = driver.isVerified
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = driver.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (driver.isSimulated) {
                        Spacer(Modifier.width(8.dp))
                        TagPill(text = "demo", color = TextSecondary)
                    }
                }
                RatingRow(rating = driver.rating, tripCount = driver.tripCount)
                TagPill(
                    text = driver.vehicleType.label,
                    color = SuccessGreen,
                    leading = {
                        Icon(
                            imageVector = driver.vehicleType.icon(),
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${driver.etaMinutes} min",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "A ${AppViewModel.formatKm(driver.distanceKm)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/** Compact map pin for a driver's vehicle. */
@Composable
fun VehiclePin(vehicleType: VehicleType, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier.size(40.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = GoldAccent,
        shadowElevation = 6.dp
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = vehicleType.icon(),
                contentDescription = vehicleType.label,
                tint = com.rork.ananego.ui.theme.JungleDeep,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun SpacerHeight(height: Int) {
    Spacer(Modifier.height(height.dp))
}
