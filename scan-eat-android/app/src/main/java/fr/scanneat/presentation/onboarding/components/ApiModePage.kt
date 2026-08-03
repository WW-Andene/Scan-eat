package fr.scanneat.presentation.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ColumnScope.ApiModePage(
    selectedMode: ApiMode, onModeChange: (ApiMode) -> Unit,
    apiKey: String, onApiKeyChange: (String) -> Unit,
    apiKeyVisible: Boolean, onToggleApiKeyVisible: () -> Unit,
    serverUrl: String, onServerUrlChange: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Text(stringResource(R.string.onboarding_config_title), style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)
    Text(
        stringResource(R.string.onboarding_config_body),
        style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.7f), textAlign = TextAlign.Center,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
        ModeCard(
            selected  = selectedMode == ApiMode.DIRECT,
            title     = stringResource(R.string.onboarding_mode_direct_title),
            subtitle  = stringResource(R.string.onboarding_mode_direct_subtitle),
            onClick   = { onModeChange(ApiMode.DIRECT) },
        )
        ModeCard(
            selected  = selectedMode == ApiMode.SERVER,
            title     = stringResource(R.string.onboarding_mode_server_title),
            subtitle  = stringResource(R.string.onboarding_mode_server_subtitle),
            onClick   = { onModeChange(ApiMode.SERVER) },
        )
    }

    if (selectedMode == ApiMode.DIRECT) {
        OutlinedTextField(
            value = apiKey, onValueChange = onApiKeyChange,
            label = { Text(stringResource(R.string.onboarding_api_key_label)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleApiKeyVisible) {
                    Icon(if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, stringResource(R.string.settings_toggle_key_visibility), tint = OnBackground.copy(0.6f))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = scanEatTextFieldColors(),
            shape = RoundedCornerShape(CardRadius.CONTROL),
        )
        Text(stringResource(R.string.onboarding_api_key_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
    } else {
        OutlinedTextField(
            value = serverUrl, onValueChange = onServerUrlChange,
            label = { Text(stringResource(R.string.settings_server_url)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            colors = scanEatTextFieldColors(),
            shape = RoundedCornerShape(CardRadius.CONTROL),
        )
        // Direct mode's key field gets a hint caption below it; Server mode
        // (the more technical, more error-prone path) had none at all.
        Text(stringResource(R.string.onboarding_server_url_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
    }

    Spacer(Modifier.weight(1f))
    ScanEatPrimaryButton(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
        enabled = (selectedMode == ApiMode.DIRECT && apiKey.isNotBlank()) ||
                  (selectedMode == ApiMode.SERVER && serverUrl.isNotBlank()),
    ) { Text(stringResource(R.string.onboarding_continue_button), style = MaterialTheme.typography.titleMedium) }
    TextButton(
        onClick = onSkip,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.onboarding_api_skip), color = OnBackground.copy(0.5f)) }
}
