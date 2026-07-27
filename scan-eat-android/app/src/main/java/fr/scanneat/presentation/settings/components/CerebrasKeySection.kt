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
internal fun CerebrasKeySection(
    localCerebrasKey: String, onLocalCerebrasKeyChange: (String) -> Unit,
    cerebrasKeyVisible: Boolean, onToggleVisible: () -> Unit,
    saved: Boolean, onSave: () -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_section_cerebras_key)) {
        Text(stringResource(R.string.settings_cerebras_key_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        OutlinedTextField(
            value = localCerebrasKey, onValueChange = onLocalCerebrasKeyChange,
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_cerebras_key_placeholder)) },
            visualTransformation = if (cerebrasKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleVisible) {
                    Icon(if (cerebrasKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.settings_toggle_key_visibility), tint = OnBackground.copy(0.6f))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true, shape = RoundedCornerShape(CardRadius.CONTROL),
            colors = scanEatTextFieldColors(),
        )
        SaveButtonRow(saved = saved, onSave = onSave)
    }
}
