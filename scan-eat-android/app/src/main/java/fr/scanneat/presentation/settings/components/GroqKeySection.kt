package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun GroqKeySection(
    localKey: String, onLocalKeyChange: (String) -> Unit,
    keyVisible: Boolean, onToggleVisible: () -> Unit,
    saved: Boolean, onSave: () -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_section_groq_key)) {
        OutlinedTextField(
            value = localKey, onValueChange = onLocalKeyChange,
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_groq_key_placeholder)) },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleVisible) {
                    Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.settings_toggle_key_visibility), tint = OnBackground.copy(0.6f))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true, shape = RoundedCornerShape(CardRadius.CONTROL),
            colors = scanEatTextFieldColors(),
        )
        Text(stringResource(R.string.onboarding_api_key_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
        SaveButtonRow(saved = saved, onSave = onSave)
    }
}
