package com.rork.ananego.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rork.ananego.data.model.Driver
import com.rork.ananego.data.model.VehicleType
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
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
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) GoldAccent else JungleOutline.copy(alpha = 0.6f),
        animationSpec = tween(250),
        label = "driverBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isHighlighted) 2.dp else 1.dp,
        animationSpec = tween(250),
        label = "driverBorderWidth"
    )
    JungleCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(borderWidth, borderColor)
    ) {
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

/**
 * Compact map pin for a driver's vehicle: green for mototaxis, gold for cars.
 * A selected pin grows slightly so the tapped marker stands out.
 */
@Composable
fun VehiclePin(vehicleType: VehicleType, modifier: Modifier = Modifier, isSelected: Boolean = false) {
    val isMototaxi = vehicleType == VehicleType.MOTOTAXI
    val pinSize by animateDpAsState(
        targetValue = if (isSelected) 48.dp else 40.dp,
        animationSpec = tween(200),
        label = "pinSize"
    )
    androidx.compose.material3.Surface(
        modifier = modifier.size(pinSize),
        shape = CircleShape,
        color = if (isMototaxi) SuccessGreen else GoldAccent,
        border = BorderStroke(2.dp, Color.White),
        shadowElevation = if (isSelected) 10.dp else 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = vehicleType.icon(),
                contentDescription = vehicleType.label,
                tint = if (isMototaxi) Color.White else JungleDeep,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun SpacerHeight(height: Int) {
    Spacer(Modifier.height(height.dp))
}
