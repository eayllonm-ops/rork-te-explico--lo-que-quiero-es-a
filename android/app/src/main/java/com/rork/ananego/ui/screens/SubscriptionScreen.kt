package com.rork.ananego.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.ananego.data.model.Subscription
import com.rork.ananego.data.model.VehicleType
import com.rork.ananego.ui.components.JungleCard
import com.rork.ananego.ui.components.ProgressRing
import com.rork.ananego.ui.components.clickableCard
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.DangerRed
import com.rork.ananego.ui.theme.JungleCanvas
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.JungleSurface
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

private data class PaymentMethod(val id: String, val label: String, val detail: String, val icon: ImageVector)

private val paymentMethods = listOf(
    PaymentMethod("yape", "Yape / Plin", "Pago inmediato desde tu celular", Icons.Filled.PhoneAndroid),
    PaymentMethod("cash", "Efectivo en agente", "Paga en puntos autorizados de Satipo", Icons.Filled.Payments),
    PaymentMethod("card", "Tarjeta", "Débito o crédito, cobro automático", Icons.Filled.CreditCard)
)

/** Weekly subscription detail: the driver's flat fee instead of per-trip commission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    subscription: Subscription,
    onBack: () -> Unit,
    onRenew: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMethod by remember { mutableStateOf(paymentMethods.first().id) }

    Scaffold(
        modifier = modifier,
        containerColor = JungleCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Mi suscripción", style = MaterialTheme.typography.titleMedium) },
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
                Button(
                    onClick = onRenew,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldAccent,
                        contentColor = JungleDeep
                    )
                ) {
                    Text(
                        text = "Pagar S/ ${subscription.weeklyFeeSoles}.00 esta semana",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            JungleCard(modifier = Modifier.fillMaxWidth(), color = JungleSurface) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProgressRing(
                        progress = subscription.progress,
                        ringColor = if (subscription.isActive) GoldAccent else DangerRed,
                        modifier = Modifier.size(140.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${subscription.daysRemaining}",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "días restantes",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Text(
                        text = if (subscription.isActive) "Suscripción activa" else "Suscripción vencida",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subscription.renewsOnLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            JungleCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Tu plan",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PlanRow(
                        title = "Mototaxi",
                        price = "S/ 5.00 / semana",
                        isCurrent = subscription.vehicleType == VehicleType.MOTOTAXI
                    )
                    PlanRow(
                        title = "Auto interprovincial",
                        price = "S/ 10.00 / semana",
                        isCurrent = subscription.vehicleType == VehicleType.INTERCITY_CAR
                    )
                    listOf(
                        "Viajes ilimitados sin comisión por carrera",
                        "Todo lo que cobras al pasajero es tuyo",
                        "Aparece en el mapa de pasajeros de Satipo"
                    ).forEach { benefit ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = benefit,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Text(
                text = "Método de pago",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            paymentMethods.forEach { method ->
                val isSelected = selectedMethod == method.id
                JungleCard(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, if (isSelected) GoldAccent else JungleOutline)
                ) {
                    Row(
                        modifier = Modifier
                            .clickableCard { selectedMethod = method.id }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = method.icon,
                            contentDescription = null,
                            tint = if (isSelected) GoldAccent else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = method.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = method.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        androidx.compose.material3.RadioButton(
                            selected = isSelected,
                            onClick = { selectedMethod = method.id },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                selectedColor = GoldAccent,
                                unselectedColor = JungleOutline
                            )
                        )
                    }
                }
            }

            Box(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlanRow(title: String, price: String, isCurrent: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) GoldAccent else MaterialTheme.colorScheme.onSurface
            )
            Text(text = price, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (isCurrent) {
            Surface(shape = RoundedCornerShape(50), color = GoldAccent) {
                Text(
                    text = "Tu plan",
                    style = MaterialTheme.typography.labelSmall,
                    color = JungleDeep,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
