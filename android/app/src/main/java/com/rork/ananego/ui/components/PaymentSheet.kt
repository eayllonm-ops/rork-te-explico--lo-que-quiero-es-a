package com.rork.ananego.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.rork.ananego.data.model.PaymentMethod
import com.rork.ananego.data.model.Ride
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.GoldShine
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.JungleSurface
import com.rork.ananego.ui.theme.JungleSurfaceHigh
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

/** Formats 987654321 as "987 654 321" so it's easy to type into Yape / Plin. */
fun formatPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return if (digits.length == 9) "${digits.take(3)} ${digits.substring(3, 6)} ${digits.takeLast(3)}" else digits
}

/**
 * Settlement sheet shown when a trip ends (or on demand): for Yape / Plin it
 * shows the driver's QR and number to scan or transfer right away; for cash,
 * the amount to hand over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSheet(
    ride: Ride,
    qrUrl: String?,
    onDismiss: () -> Unit
) {
    val driver = ride.driver
    val isDigital = ride.paymentMethod == PaymentMethod.YAPE_PLIN
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val payoutNumber = driver?.payoutPhone?.ifBlank { driver.phone }.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = JungleSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isDigital) Icons.Filled.QrCode2 else Icons.Filled.Payments,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isDigital) "Paga con Yape / Plin" else "Paga en efectivo",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = AppViewModel.formatSoles(ride.fareSoles),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = GoldShine
            )
            Text(
                text = listOfNotNull(driver?.name, "${ride.origin.name} → ${ride.destination.name}")
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            if (isDigital) {
                QrBlock(qrUrl = qrUrl.takeIf { driver?.hasPaymentQr == true })

                if (payoutNumber.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = JungleSurfaceHigh,
                        border = BorderStroke(1.dp, JungleOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Número Yape / Plin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = formatPhone(payoutNumber),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(payoutNumber))
                                    copied = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (copied) SuccessGreen else GoldAccent)
                            ) {
                                Icon(
                                    imageVector = if (copied) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    tint = if (copied) SuccessGreen else GoldAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (copied) "Copiado" else "Copiar",
                                    color = if (copied) SuccessGreen else GoldAccent
                                )
                            }
                        }
                    }
                } else if (driver?.hasPaymentQr != true) {
                    Text(
                        text = if (driver?.isSimulated == true) {
                            "Conductor demo: no tiene Yape / Plin. En un viaje real verás su QR y número aquí."
                        } else {
                            "El conductor aún no registró su Yape / Plin. Pídele su número o paga en efectivo."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Entrega el monto exacto al conductor al bajar. Añane Go no cobra comisión por viaje.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = JungleDeep)
            ) {
                Text(
                    text = if (isDigital) "Ya pagué" else "Listo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QrBlock(qrUrl: String?) {
    // White card so any phone camera or the Yape / Plin scanner reads it reliably.
    Box(
        modifier = Modifier
            .size(252.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (qrUrl != null) Color.White else JungleSurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        if (qrUrl == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.QrCode2,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Sin QR registrado",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = qrUrl,
                contentDescription = "Código QR de Yape / Plin del conductor",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(236.dp)
                    .clip(RoundedCornerShape(12.dp)),
                loading = {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = JungleDeep, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                },
                error = {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "No pudimos cargar el QR",
                            style = MaterialTheme.typography.labelMedium,
                            color = JungleDeep
                        )
                    }
                }
            )
        }
    }
}

/** Two-option selector (Efectivo / Yape-Plin) used when requesting a ride. */
@Composable
fun PaymentMethodSelector(
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PaymentMethod.entries.forEach { method ->
            val isSelected = method == selected
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) GoldAccent.copy(alpha = 0.16f) else JungleSurfaceHigh,
                border = BorderStroke(1.dp, if (isSelected) GoldAccent else JungleOutline),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .clickableCard { onSelect(method) }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) GoldAccent else JungleOutline),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (method == PaymentMethod.CASH) Icons.Filled.Payments else Icons.Filled.QrCode2,
                            contentDescription = null,
                            tint = if (isSelected) JungleDeep else TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = method.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) GoldAccent else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
