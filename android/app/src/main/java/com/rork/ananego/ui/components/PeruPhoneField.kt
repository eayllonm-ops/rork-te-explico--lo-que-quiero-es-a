package com.rork.ananego.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rork.ananego.ui.theme.DangerRed
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.JungleOutline
import com.rork.ananego.ui.theme.SuccessGreen
import com.rork.ananego.ui.theme.TextSecondary

/** Peruvian mobile rules: exactly 9 digits and starting with 9. */
object PeruPhone {
    const val LENGTH = 9

    /** Keeps only digits, drops a pasted +51 prefix and caps at 9. */
    fun sanitize(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        val local = if (digits.length > LENGTH && digits.startsWith("51")) digits.drop(2) else digits
        return local.take(LENGTH)
    }

    fun isValid(phone: String): Boolean =
        phone.length == LENGTH && phone.first() == '9' && phone.all { it.isDigit() }

    /** Live message while typing; null when the field is empty or valid. */
    fun error(phone: String): String? = when {
        phone.isEmpty() -> null
        phone.first() != '9' -> "El celular debe empezar con 9"
        phone.length < LENGTH -> {
            val missing = LENGTH - phone.length
            "Faltan $missing ${if (missing == 1) "dígito" else "dígitos"}"
        }
        else -> null
    }
}

/**
 * Phone input with real-time validation: red border and message while the
 * number is wrong, green check once it is a valid Peruvian mobile.
 */
@Composable
fun PeruPhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String = "9 dígitos, empieza con 9"
) {
    val error = PeruPhone.error(value)
    val isValid = PeruPhone.isValid(value)

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(PeruPhone.sanitize(it)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, color = if (error != null) DangerRed else TextSecondary) },
        placeholder = { Text("987654321", color = TextSecondary.copy(alpha = 0.5f)) },
        prefix = { Text("+51 ", color = TextSecondary) },
        singleLine = true,
        isError = error != null,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        trailingIcon = when {
            error != null -> {
                { Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = DangerRed) }
            }
            isValid -> {
                { Icon(Icons.Filled.CheckCircle, contentDescription = "Número válido", tint = SuccessGreen) }
            }
            else -> null
        },
        supportingText = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = error ?: if (isValid) "Número válido" else helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        error != null -> DangerRed
                        isValid -> SuccessGreen
                        else -> TextSecondary
                    }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${value.length}/${PeruPhone.LENGTH}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isValid) SuccessGreen else GoldAccent,
            unfocusedBorderColor = if (isValid) SuccessGreen.copy(alpha = 0.7f) else JungleOutline,
            errorBorderColor = DangerRed,
            cursorColor = GoldAccent,
            errorCursorColor = DangerRed,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            errorTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedLabelColor = GoldAccent
        )
    )
}
