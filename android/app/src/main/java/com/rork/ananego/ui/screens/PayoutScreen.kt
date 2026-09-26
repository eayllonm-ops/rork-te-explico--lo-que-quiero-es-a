package com.rork.ananego.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rork.ananego.data.model.VerificationPhoto
import com.rork.ananego.ui.components.JungleCard
import com.rork.ananego.ui.components.clickableCard
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.JungleCanvas
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.JungleSurfaceHigh
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

/**
 * Standalone screen where a (verified) driver updates the contact mobile and
 * Yape / Plin details instantly, without resubmitting documents or losing approval.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayoutScreen(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { viewModel.startRegistrationDraft() }
    Scaffold(
        modifier = modifier,
        containerColor = JungleCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Contacto y cobros", style = MaterialTheme.typography.titleMedium) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Los cambios se guardan al instante y no afectan tu aprobación como conductor.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            ContactPhoneCard(viewModel = viewModel)
            PayoutCard(viewModel = viewModel)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Contact mobile the passenger reaches with one tap (Llamar / WhatsApp) during the ride. */
@Composable
fun ContactPhoneCard(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    var number by remember(draft.phone) { mutableStateOf(draft.phone) }
    val isSaved = draft.phone.length == 9 && number == draft.phone

    JungleCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SuccessGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Celular de contacto",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (draft.phone.length == 9) {
                            "Tus pasajeros te llaman o escriben por WhatsApp con un toque"
                        } else {
                            "Sin celular tus pasajeros no podrán contactarte"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (draft.phone.length == 9) TextSecondary else GoldAccent
                    )
                }
            }

            OutlinedTextField(
                value = number,
                onValueChange = { value -> number = value.filter { it.isDigit() }.take(9) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Número de celular", color = TextSecondary) },
                placeholder = { Text("987654321", color = TextSecondary.copy(alpha = 0.5f)) },
                prefix = { Text("+51 ", color = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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

            Button(
                onClick = { viewModel.saveDriverContact(contactPhone = number, payoutPhone = null) },
                enabled = number.length == 9 && !isSaved,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = JungleDeep,
                    disabledContainerColor = JungleSurfaceHigh,
                    disabledContentColor = TextSecondary
                )
            ) {
                if (isSaved) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (isSaved) "Celular guardado" else "Guardar celular",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Yape / Plin setup: QR image picked from the gallery (the screenshot saved
 * from the Yape or bank app) plus the linked phone number. Saved instantly,
 * never part of the identity review.
 */
@Composable
fun PayoutCard(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isUploading = VerificationPhoto.PAYMENT_QR in uiState.uploadingPhotos
    var number by remember(draft.payoutPhone, draft.phone) {
        mutableStateOf(draft.payoutPhone.ifBlank { draft.phone })
    }
    // Bumped after each upload so Coil refetches the replaced QR instead of a cached one.
    var qrVersion by remember { mutableStateOf(0) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        if (picked != null) {
            viewModel.uploadPhoto(VerificationPhoto.PAYMENT_QR, picked)
            qrVersion += 1
        }
    }

    JungleCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(
                    text = "Cobros con Yape / Plin (opcional)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sube la imagen de tu QR y tu número asociado",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(112.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (draft.hasPaymentQr) Color.White else JungleSurfaceHigh,
                    border = BorderStroke(1.dp, if (draft.hasPaymentQr) SuccessGreen else JungleOutline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickableCard {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (draft.hasPaymentQr && !isUploading) {
                            AsyncImage(
                                model = "${viewModel.verificationPhotoUrl(VerificationPhoto.PAYMENT_QR)}&v=$qrVersion",
                                contentDescription = "Tu QR de Yape / Plin",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else if (!isUploading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.QrCode2, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("Subir QR", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            }
                        }
                        if (isUploading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(JungleDeep.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = GoldAccent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (draft.hasPaymentQr) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("QR guardado", style = MaterialTheme.typography.labelLarge, color = SuccessGreen)
                        }
                    }
                    Text(
                        text = "En Yape: Mi QR → Descargar. Luego elige esa imagen aquí.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.clickableCard {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (draft.hasPaymentQr) "Cambiar imagen" else "Elegir de la galería",
                            style = MaterialTheme.typography.labelLarge,
                            color = GoldAccent
                        )
                    }
                }
            }

            OutlinedTextField(
                value = number,
                onValueChange = { value -> number = value.filter { it.isDigit() }.take(9) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Número Yape / Plin", color = TextSecondary) },
                placeholder = { Text("987654321", color = TextSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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

            Button(
                onClick = { viewModel.savePayoutPhone(number) },
                enabled = number.length == 9 && number != draft.payoutPhone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = JungleDeep,
                    disabledContainerColor = JungleSurfaceHigh,
                    disabledContentColor = TextSecondary
                )
            ) {
                Text(
                    text = if (number.isNotEmpty() && number == draft.payoutPhone) "Número guardado" else "Guardar número",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
