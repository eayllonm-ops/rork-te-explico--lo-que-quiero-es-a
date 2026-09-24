package com.rork.ananego.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rork.ananego.data.model.DriverProfile
import com.rork.ananego.data.model.VehicleType
import com.rork.ananego.data.model.VerificationPhoto
import com.rork.ananego.data.model.VerificationStatus
import com.rork.ananego.ui.components.BrandLockup
import com.rork.ananego.ui.components.JungleCard
import com.rork.ananego.ui.components.TagPill
import com.rork.ananego.ui.components.clickableCard
import com.rork.ananego.ui.components.icon
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.DangerRed
import com.rork.ananego.ui.theme.JungleCanvas
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.JungleSurface
import com.rork.ananego.ui.theme.JungleSurfaceHigh
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary
import java.io.File

/**
 * Full-screen driver onboarding: profile photo, DNI (both sides), vehicle
 * ownership card, plate text and a plate photo. Every document is captured
 * with the camera (or picked from the gallery) and uploaded to the city
 * service, where the review team verifies it officially.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRegistrationScreen(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Which slot the source dialog is open for, and which capture is in flight.
    var dialogKind by remember { mutableStateOf<VerificationPhoto?>(null) }
    var captureKind by remember { mutableStateOf<VerificationPhoto?>(null) }
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val kind = captureKind
        val target = cameraTarget
        captureKind = null
        cameraTarget = null
        if (captured && kind != null && target != null) viewModel.uploadPhoto(kind, target)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        val kind = captureKind
        captureKind = null
        if (picked != null && kind != null) viewModel.uploadPhoto(kind, picked)
    }

    LaunchedEffect(Unit) { viewModel.startRegistrationDraft() }

    dialogKind?.let { kind ->
        PhotoSourceDialog(
            title = kind.label,
            onDismiss = { dialogKind = null },
            onCamera = {
                val dir = File(context.cacheDir, "ananego").apply { mkdirs() }
                val file = File(dir, "${kind.name.lowercase()}_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                captureKind = kind
                dialogKind = null
                cameraTarget = uri
                cameraLauncher.launch(uri)
            },
            onGallery = {
                captureKind = kind
                dialogKind = null
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }

    val openCapture: (VerificationPhoto) -> Unit = { kind -> dialogKind = kind }
    val isUploading: (VerificationPhoto) -> Boolean = { kind -> kind in uiState.uploadingPhotos }

    Scaffold(
        modifier = modifier,
        containerColor = JungleCanvas,
        topBar = {
            TopAppBar(
                title = { BrandLockup(showTagline = false) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar registro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JungleCanvas,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(color = JungleCanvas) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AnimatedVisibility(visible = !draft.isComplete) {
                        Text(
                            text = "Completa tus datos y sube las fotos de tus documentos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    Button(
                        onClick = {
                            if (viewModel.submitRegistration()) onClose()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        enabled = draft.isComplete && uiState.uploadingPhotos.isEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = JungleDeep,
                            disabledContainerColor = JungleSurfaceHigh,
                            disabledContentColor = TextSecondary
                        )
                    ) {
                        Text(
                            text = if (uiState.uploadingPhotos.isEmpty()) {
                                "Enviar para verificación"
                            } else {
                                "Subiendo documentos…"
                            },
                            style = MaterialTheme.typography.titleMedium,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Registro de conductor",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Verifica tu identidad para empezar a recibir viajes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            if (draft.status != VerificationStatus.NOT_STARTED) {
                TagPill(
                    text = draft.status.label,
                    color = if (draft.status == VerificationStatus.VERIFIED) SuccessGreen else GoldAccent,
                    leading = {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = if (draft.status == VerificationStatus.VERIFIED) SuccessGreen else GoldAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                )
            }

            if (draft.status == VerificationStatus.REJECTED && draft.rejectionReason.isNotBlank()) {
                Text(
                    text = "Motivo del rechazo: ${draft.rejectionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DangerRed
                )
            }

            ProfilePhotoCard(
                uploaded = draft.hasProfilePhoto,
                uploading = isUploading(VerificationPhoto.PROFILE),
                photoUrl = viewModel.verificationPhotoUrl(VerificationPhoto.PROFILE),
                onCapture = { openCapture(VerificationPhoto.PROFILE) }
            )

            JungleCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Datos personales",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    JungleTextField(
                        value = draft.fullName,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(fullName = value) } },
                        label = "Nombres y apellidos",
                        capitalization = KeyboardCapitalization.Words
                    )
                    JungleTextField(
                        value = draft.dni,
                        onValueChange = { value ->
                            val digits = value.filter { it.isDigit() }.take(8)
                            viewModel.updateDraft { it.copy(dni = digits) }
                        },
                        label = "Número de DNI",
                        keyboardType = KeyboardType.Number
                    )
                }
            }

            DocumentCaptureCard(
                title = "DNI (frente y reverso)",
                subtitle = "Sube fotos claras de tu DNI",
                icon = Icons.Filled.Badge,
                uploaded = { kind ->
                    when (kind) {
                        VerificationPhoto.DNI_FRONT -> draft.hasDniFront
                        VerificationPhoto.DNI_BACK -> draft.hasDniBack
                        else -> false
                    }
                },
                photoUrl = { kind -> viewModel.verificationPhotoUrl(kind) },
                isUploading = isUploading,
                onCapture = openCapture,
                slots = listOf(VerificationPhoto.DNI_FRONT, VerificationPhoto.DNI_BACK)
            )

            DocumentCaptureCard(
                title = "Tarjeta de propiedad del vehículo",
                subtitle = "Sube una foto de la tarjeta de propiedad",
                icon = Icons.Filled.UploadFile,
                uploaded = { kind -> kind == VerificationPhoto.VEHICLE_CARD && draft.hasVehicleCard },
                photoUrl = { kind -> viewModel.verificationPhotoUrl(kind) },
                isUploading = isUploading,
                onCapture = openCapture,
                slots = listOf(VerificationPhoto.VEHICLE_CARD)
            )

            JungleCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(
                            text = "Placa del vehículo",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ingresa la placa y sube una foto legible",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    JungleTextField(
                        value = draft.plate,
                        onValueChange = { value ->
                            viewModel.updateDraft { it.copy(plate = value.uppercase().take(8)) }
                        },
                        label = "AB-1234",
                        capitalization = KeyboardCapitalization.Characters
                    )
                    CaptureSlot(
                        kind = VerificationPhoto.PLATE,
                        icon = Icons.Filled.PhotoCamera,
                        photoUrl = viewModel.verificationPhotoUrl(VerificationPhoto.PLATE),
                        uploaded = draft.hasPlatePhoto,
                        uploading = isUploading(VerificationPhoto.PLATE),
                        onCapture = { openCapture(VerificationPhoto.PLATE) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 96.dp
                    )
                }
            }

            JungleCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(
                            text = "Tipo de unidad",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Define tu suscripción semanal",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VehicleType.entries.forEach { type ->
                            VehicleOption(
                                type = type,
                                isSelected = draft.vehicleType == type,
                                onSelect = { viewModel.updateDraft { it.copy(vehicleType = type) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            ChecklistSummary(profile = draft)

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PhotoSourceDialog(
    title: String,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JungleSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = TextSecondary,
        title = { Text("Subir $title") },
        text = { Text("Elige cómo quieres capturar el documento") },
        confirmButton = {
            TextButton(onClick = onCamera) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Tomar foto", color = GoldAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onGallery) {
                Icon(
                    Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Galería", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}

@Composable
private fun ProfilePhotoCard(
    uploaded: Boolean,
    uploading: Boolean,
    photoUrl: String,
    onCapture: () -> Unit
) {
    JungleCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(if (uploaded) SuccessGreen.copy(alpha = 0.22f) else JungleSurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (uploaded) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Foto de perfil subida",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    if (uploading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(JungleDeep.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = GoldAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = GoldAccent,
                    modifier = Modifier.size(28.dp)
                ) {
                    IconButton(onClick = onCapture, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = "Tomar foto de perfil",
                            tint = JungleDeep,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Foto de perfil",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        uploading -> "Subiendo foto…"
                        uploaded -> "Tu foto ya está en el servidor"
                        else -> "Tómate una foto; será visible para los pasajeros"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun DocumentCaptureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    slots: List<VerificationPhoto>,
    uploaded: (VerificationPhoto) -> Boolean,
    photoUrl: (VerificationPhoto) -> String,
    isUploading: (VerificationPhoto) -> Boolean,
    onCapture: (VerificationPhoto) -> Unit
) {
    val allUploaded = slots.all(uploaded)
    JungleCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                if (allUploaded) {
                    TagPill(
                        text = "Cargado",
                        color = SuccessGreen,
                        leading = {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                slots.forEach { kind ->
                    CaptureSlot(
                        kind = kind,
                        icon = icon,
                        photoUrl = photoUrl(kind),
                        uploaded = uploaded(kind),
                        uploading = isUploading(kind),
                        onCapture = { onCapture(kind) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureSlot(
    kind: VerificationPhoto,
    icon: ImageVector,
    photoUrl: String,
    uploaded: Boolean,
    uploading: Boolean,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp
) {
    Surface(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(16.dp),
        color = JungleSurfaceHigh,
        border = BorderStroke(
            1.dp,
            if (uploaded) SuccessGreen.copy(alpha = 0.7f) else JungleOutline
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickableCard(onCapture)
        ) {
            if (uploaded) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "${kind.label} subida",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Subir ${kind.label.lowercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
            if (uploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(JungleDeep.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = GoldAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else if (uploaded) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Foto cargada",
                    tint = SuccessGreen,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VehicleOption(
    type: VehicleType,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) GoldAccent.copy(alpha = 0.16f) else JungleSurfaceHigh,
        border = BorderStroke(1.dp, if (isSelected) GoldAccent else JungleOutline)
    ) {
        Row(
            modifier = Modifier
                .clickableCard(onSelect)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = type.icon(),
                contentDescription = null,
                tint = if (isSelected) GoldAccent else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) GoldAccent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "S/ ${type.weeklyFeeSoles}.00/sem",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = GoldAccent,
                    unselectedColor = JungleOutline
                )
            )
        }
    }
}

@Composable
private fun ChecklistSummary(profile: DriverProfile) {
    val items = listOf(
        "Foto de perfil" to profile.hasProfilePhoto,
        "DNI frente" to profile.hasDniFront,
        "DNI reverso" to profile.hasDniBack,
        "Tarjeta de propiedad" to profile.hasVehicleCard,
        "Foto de placa" to profile.hasPlatePhoto,
        "Datos completos" to (profile.fullName.isNotBlank() && profile.dni.length >= 8 && profile.plate.isNotBlank())
    )
    JungleCard(modifier = Modifier.fillMaxWidth(), color = JungleSurface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Checklist de seguridad",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            items.forEach { (label, done) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Shield,
                        contentDescription = null,
                        tint = if (done) SuccessGreen else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (done) MaterialTheme.colorScheme.onSurface else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun JungleTextField(
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
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization
        ),
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
