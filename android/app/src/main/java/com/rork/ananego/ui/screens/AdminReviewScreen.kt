package com.rork.ananego.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rork.ananego.data.model.VerificationPhoto
import com.rork.ananego.data.remote.NetworkAdminDriver
import com.rork.ananego.ui.components.JungleCard
import com.rork.ananego.ui.components.TagPill
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.DangerRed
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.GoldDeep
import com.rork.ananego.ui.theme.JungleCanvas
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.JungleSurface
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary
import androidx.compose.foundation.text.KeyboardOptions

/**
 * Hidden, PIN-protected console where the owner reviews each driver's five
 * document photos and approves or rejects them with a reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReviewScreen(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val admin by viewModel.adminState.collectAsStateWithLifecycle()
    var rejectTarget by remember { mutableStateOf<NetworkAdminDriver?>(null) }
    var viewer by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = JungleCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Revisión de conductores", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
        if (!admin.isUnlocked) {
            PinGate(
                isLoading = admin.isLoading,
                error = admin.error,
                onUnlock = viewModel::adminUnlock,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (admin.pending.isEmpty()) {
                    item {
                        JungleCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Text(
                                text = "No hay documentos pendientes de revisión.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                items(admin.pending, key = { it.id }) { driver ->
                    ReviewCard(
                        driver = driver,
                        photoUrl = { kind -> viewModel.adminPhotoUrl(driver.id, kind.name) },
                        onOpenPhoto = { kind -> viewer = driver.id to kind.name },
                        onApprove = { viewModel.adminDecide(driver.id, true, "") },
                        onReject = { rejectTarget = driver },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    rejectTarget?.let { target ->
        RejectDialog(
            driverName = target.name,
            onDismiss = { rejectTarget = null },
            onConfirm = { reason ->
                viewModel.adminDecide(target.id, false, reason)
                rejectTarget = null
            }
        )
    }

    viewer?.let { (userId, kind) ->
        PhotoViewerDialog(
            url = viewModel.adminPhotoUrl(userId, kind),
            label = kindLabel(kind),
            onDismiss = { viewer = null }
        )
    }
}

@Composable
private fun PinGate(
    isLoading: Boolean,
    error: String?,
    onUnlock: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        JungleCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Acceso restringido",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ingresa el PIN de administración para revisar documentos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> pin = value.filter { it.isDigit() }.take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PIN", color = TextSecondary) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = JungleOutline,
                        cursorColor = GoldAccent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                error?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = DangerRed)
                }
                Button(
                    onClick = { onUnlock(pin) },
                    enabled = pin.length >= 4 && !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldAccent,
                        contentColor = JungleDeep
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = JungleDeep,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text("Entrar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    driver: NetworkAdminDriver,
    photoUrl: (VerificationPhoto) -> String,
    onOpenPhoto: (VerificationPhoto) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRejected = driver.driverStatus == "REJECTED"

    JungleCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, if (isRejected) DangerRed.copy(alpha = 0.6f) else JungleOutline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driver.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "DNI ${driver.dni.ifBlank { "—" }} · " +
                            (if (driver.vehicleType == "INTERCITY_CAR") "Auto interprovincial" else "Mototaxi") +
                            " · Placa ${driver.plate.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                TagPill(
                    text = if (isRejected) "Rechazado" else "En revisión",
                    color = if (isRejected) DangerRed else GoldAccent
                )
            }

            if (isRejected && driver.rejectionReason.isNotBlank()) {
                Text(
                    text = "Motivo anterior: ${driver.rejectionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DangerRed
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val uploaded = driver.photos.toSet()
                VerificationPhoto.entries
                    .filter { it.name in uploaded }
                    .forEach { kind ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = photoUrl(kind),
                                contentDescription = kind.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(104.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(JungleDeep)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = kind.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                if (uploaded.isEmpty()) {
                    Text(
                        text = "Sin fotos registradas",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DangerRed)
                ) {
                    Text("Rechazar", color = DangerRed, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen,
                        contentColor = JungleDeep
                    )
                ) {
                    Text("Aprobar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RejectDialog(
    driverName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JungleSurface,
        title = {
            Text(
                text = "Rechazar a $driverName",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "El conductor verá este motivo y podrá corregir sus fotos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Motivo del rechazo", color = TextSecondary) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DangerRed,
                        unfocusedBorderColor = JungleOutline,
                        cursorColor = GoldAccent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DangerRed,
                    contentColor = JungleDeep
                )
            ) {
                Text("Rechazar")
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
private fun PhotoViewerDialog(
    url: String,
    label: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JungleSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = TextSecondary)
                }
            }
        },
        text = {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = GoldDeep.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .padding(6.dp)
                )
            }
        },
        confirmButton = {}
    )
}

private fun kindLabel(kind: String): String =
    VerificationPhoto.entries.firstOrNull { it.name == kind }?.label ?: kind
