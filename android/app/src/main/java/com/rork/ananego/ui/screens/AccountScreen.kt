package com.rork.ananego.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rork.ananego.data.model.AppRole
import com.rork.ananego.data.model.VerificationStatus
import com.rork.ananego.data.remote.ConnectionStatus
import com.rork.ananego.ui.components.InitialsAvatar
import com.rork.ananego.ui.components.JungleCard
import com.rork.ananego.ui.components.RatingRow
import com.rork.ananego.ui.components.SectionHeader
import com.rork.ananego.ui.components.TagPill
import com.rork.ananego.ui.components.clickableCard
import com.rork.ananego.ui.state.AppUiState
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.GoldDeep
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.JungleSurface
import com.rork.ananego.ui.theme.JungleSurfaceHigh
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

/** Account tab: identity, role switch and access to driver onboarding. */
@Composable
fun AccountScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onRoleChange: (AppRole) -> Unit,
    onOpenRegistration: () -> Unit,
    onOpenSubscription: () -> Unit,
    onSaveProfile: (String, String, String) -> Unit,
    /** Opens the hidden, PIN-protected document review console. */
    onOpenAdminReview: () -> Unit = {},
    /** Opens the driver's Yape / Plin setup. */
    onOpenPayout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isEditingIdentity by remember { mutableStateOf(false) }
    var secretTaps by remember { mutableStateOf(0) }

    if (isEditingIdentity) {
        IdentityDialog(
            initialName = state.passenger.name.takeUnless { it == "Pasajero Añane" }.orEmpty(),
            initialPhone = state.passenger.phone.takeUnless { it == "Agrega tu número" }.orEmpty(),
            initialDni = state.passenger.dni,
            onDismiss = { isEditingIdentity = false },
            onSave = { name, phone, dni ->
                isEditingIdentity = false
                onSaveProfile(name, phone, dni)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            )
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        JungleCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(
                    initials = if (state.role == AppRole.PASSENGER) {
                        state.passenger.initials
                    } else {
                        state.driverProfile.fullName.split(" ").take(2).mapNotNull { it.firstOrNull() }
                            .joinToString("").ifBlank { "AG" }
                    },
                    size = 68.dp,
                    verified = state.role == AppRole.PASSENGER ||
                        state.driverProfile.status == VerificationStatus.VERIFIED
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (state.role == AppRole.PASSENGER) {
                            state.passenger.name
                        } else {
                            state.driverProfile.fullName.ifBlank { "Conductor sin registrar" }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.passenger.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (state.role == AppRole.PASSENGER) {
                        RatingRow(rating = state.passenger.rating)
                    } else {
                        TagPill(
                            text = state.driverProfile.status.label,
                            color = if (state.driverProfile.status == VerificationStatus.VERIFIED) SuccessGreen else GoldAccent
                        )
                    }
                }
                TextButton(onClick = { isEditingIdentity = true }) {
                    Text(
                        text = "Editar",
                        color = GoldAccent,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Hidden entry: five taps on the connection card open the review console.
        ConnectionCard(
            state = state,
            onSecretTap = {
                secretTaps += 1
                if (secretTaps >= 5) {
                    secretTaps = 0
                    onOpenAdminReview()
                }
            }
        )

        SectionHeader(title = "Cómo usas Añane Go")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoleCard(
                title = "Soy pasajero",
                subtitle = "Pido mototaxi o auto",
                icon = Icons.Filled.Groups,
                isSelected = state.role == AppRole.PASSENGER,
                onClick = { onRoleChange(AppRole.PASSENGER) },
                modifier = Modifier.weight(1f)
            )
            RoleCard(
                title = "Soy conductor",
                subtitle = "Recibo solicitudes",
                icon = Icons.Filled.WorkspacePremium,
                isSelected = state.role == AppRole.DRIVER,
                onClick = { onRoleChange(AppRole.DRIVER) },
                modifier = Modifier.weight(1f)
            )
        }

        SectionHeader(title = "Seguridad e identidad")

        JungleCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                MenuRow(
                    icon = Icons.Filled.Badge,
                    title = "Registro de conductor",
                    subtitle = when (state.driverProfile.status) {
                        VerificationStatus.VERIFIED -> "Documentos verificados"
                        VerificationStatus.PENDING -> "En revisión"
                        VerificationStatus.REJECTED -> "Vuelve a enviar tus documentos"
                        VerificationStatus.NOT_STARTED -> "Sube tu DNI y tarjeta de propiedad"
                    },
                    onClick = onOpenRegistration
                )
                Divider()
                MenuRow(
                    icon = Icons.Filled.Shield,
                    title = "Verificación de pasajero",
                    subtitle = if (state.passenger.isVerified) {
                        "DNI ${state.passenger.dni} · verificado"
                    } else {
                        "Agrega tu nombre y DNI para viajar verificado"
                    },
                    onClick = { isEditingIdentity = true }
                )
                Divider()
                MenuRow(
                    icon = Icons.Filled.QrCode2,
                    title = "Cobros con Yape / Plin",
                    subtitle = when {
                        state.driverProfile.hasPaymentQr -> "QR cargado · los pasajeros te pagan al instante"
                        state.driverProfile.payoutPhone.isNotBlank() -> "Número ${state.driverProfile.payoutPhone} · sube tu QR"
                        else -> "Sube tu QR y número para cobrar sin efectivo"
                    },
                    onClick = onOpenPayout
                )
                Divider()
                MenuRow(
                    icon = Icons.Filled.Payments,
                    title = "Mi suscripción semanal",
                    subtitle = "S/ ${state.subscription.weeklyFeeSoles}.00 · ${state.subscription.renewsOnLabel}",
                    onClick = onOpenSubscription
                )
            }
        }

        SectionHeader(title = "Preferencias de viaje")

        JungleCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                PreferenceToggle(
                    icon = Icons.Filled.Accessible,
                    title = "Necesito unidad accesible",
                    subtitle = "Avisamos al conductor antes de aceptar",
                    checked = state.passenger.defaultPreferences.any { it.name == "WHEELCHAIR" }
                )
                Divider()
                PreferenceToggle(
                    icon = Icons.Filled.Notifications,
                    title = "Avisos de llegada",
                    subtitle = "Notificación cuando el conductor esté cerca",
                    checked = true
                )
            }
        }

        JungleCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                MenuRow(
                    icon = Icons.Filled.SupportAgent,
                    title = "Soporte Añane Go",
                    subtitle = "Escríbenos por WhatsApp",
                    onClick = { }
                )
                Divider()
                MenuRow(
                    icon = Icons.Filled.Phone,
                    title = "Contacto de emergencia",
                    subtitle = "Comparte tu viaje con un familiar",
                    onClick = { }
                )
            }
        }
    }
}

/** Shows whether this device is talking to the live Añane Go network. */
@Composable
private fun ConnectionCard(state: AppUiState, onSecretTap: () -> Unit = {}) {
    val (label, detail, color) = when (state.connection) {
        ConnectionStatus.LIVE -> Triple(
            "Conectado a la red Añane Go",
            "Ves conductores y solicitudes en tiempo real",
            SuccessGreen
        )
        ConnectionStatus.CONNECTING -> Triple(
            "Conectando con Satipo…",
            "Buscando la red de conductores",
            GoldAccent
        )
        ConnectionStatus.OFFLINE -> Triple(
            "Sin conexión con la red",
            "Revisa tu internet, reintentamos solos",
            GoldDeep
        )
    }
    JungleCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .clickableCard(onSecretTap)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(50), color = color, modifier = Modifier.size(10.dp)) {}
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.titleSmall, color = color)
                Text(text = detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

/** Lets the passenger publish a real name and DNI to the network. */
@Composable
private fun IdentityDialog(
    initialName: String,
    initialPhone: String,
    initialDni: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var dni by remember { mutableStateOf(initialDni) }
    val canSave = name.trim().length > 2 && dni.length >= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JungleSurface,
        title = {
            Text(
                text = "Tu identidad",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Los conductores ven tu nombre antes de aceptar el viaje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                AccountField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Nombres y apellidos",
                    capitalization = KeyboardCapitalization.Words
                )
                AccountField(
                    value = phone,
                    onValueChange = { phone = it.filter { char -> char.isDigit() || char == '+' }.take(12) },
                    label = "Celular",
                    keyboardType = KeyboardType.Phone
                )
                AccountField(
                    value = dni,
                    onValueChange = { dni = it.filter { char -> char.isDigit() }.take(8) },
                    label = "DNI",
                    keyboardType = KeyboardType.Number
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), phone.trim(), dni.trim()) },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = JungleDeep,
                    disabledContainerColor = JungleSurfaceHigh,
                    disabledContentColor = TextSecondary
                )
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun AccountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = TextSecondary) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GoldAccent,
            unfocusedBorderColor = JungleOutline,
            cursorColor = GoldAccent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedLabelColor = GoldAccent
        )
    )
}

@Composable
private fun Divider() {
    Surface(
        color = JungleOutline.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {}
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) GoldAccent else JungleSurface,
        border = BorderStroke(1.dp, if (isSelected) GoldAccent else JungleOutline)
    ) {
        Column(
            modifier = Modifier
                .clickableCard(onClick)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) JungleDeep else GoldDeep,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) JungleDeep else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) JungleDeep.copy(alpha = 0.75f) else TextSecondary
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableCard(onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JungleSurfaceHigh,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.padding(9.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary
        )
    }
}

@Composable
private fun PreferenceToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GoldAccent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JungleDeep,
                checkedTrackColor = GoldAccent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = JungleSurfaceHigh
            )
        )
    }
}
