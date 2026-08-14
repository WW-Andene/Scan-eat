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

/**
 * Shared shape for GroqKeySection/CerebrasKeySection - both were copy-pasted
 * identical (masked API key field with a visibility-toggle trailing icon,
 * plus a save button) with only the hint text's position differing (Groq's
 * appears after the field, Cerebras's before it). Extracted so a future
 * change to this pattern (e.g. the visibility-toggle icon) only needs to
 * happen once.
 */
@Composable
internal fun ApiKeyInputSection(
    titleRes: Int,
    fieldLabelRes: Int,
    localKey: String, onLocalKeyChange: (String) -> Unit,
    keyVisible: Boolean, onToggleVisible: () -> Unit,
    saved: Boolean, onSave: () -> Unit,
    hintBeforeRes: Int? = null,
    hintAfterRes: Int? = null,
) {
    SettingsSection(stringResource(titleRes), icon = Icons.Default.Key) {
        hintBeforeRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f)) }
        OutlinedTextField(
            value = localKey, onValueChange = onLocalKeyChange,
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(fieldLabelRes)) },
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
        hintAfterRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f)) }
        SaveButtonRow(saved = saved, onSave = onSave)
    }
}
