package fr.scanneat.presentation.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.presentation.ui.theme.*

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onDone: () -> Unit,
    onGoToProfile: () -> Unit = {},
) {
    val done = viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done.value) { if (done.value) onDone() }

    var page by remember { mutableIntStateOf(0) }
    var selectedMode by remember { mutableStateOf(ApiMode.DIRECT) }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("") }

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.XL),
        ) {
            Spacer(Modifier.height(40.dp))

            when (page) {
                // ---- Page 0: Welcome ----
                0 -> {
                    Icon(Icons.Default.QrCodeScanner, null, tint = AccentCoral, modifier = Modifier.size(64.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = OnBackground, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.onboarding_welcome_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnBackground.copy(0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    ScanEatPrimaryButton(
                        onClick = { page = 1 },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_start_button), fontSize = 16.sp) }
                }

                // ---- Page 1: Value proposition — what sets this apart ----
                1 -> {
                    Text(stringResource(R.string.onboarding_value_title), style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        ValueCard(
                            icon    = Icons.Default.Fingerprint,
                            title   = stringResource(R.string.onboarding_value_transparency_title),
                            body    = stringResource(R.string.onboarding_value_transparency_body),
                        )
                        ValueCard(
                            icon    = Icons.Default.MonitorHeart,
                            title   = stringResource(R.string.onboarding_value_biolism_title),
                            body    = stringResource(R.string.onboarding_value_biolism_body),
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    ScanEatPrimaryButton(
                        onClick = { page = 2 },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_continue_button), fontSize = 16.sp) }
                }

                // ---- Page 2: API mode ----
                2 -> {
                    Text(stringResource(R.string.onboarding_config_title), style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.onboarding_config_body),
                        style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.7f), textAlign = TextAlign.Center,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeCard(
                            selected  = selectedMode == ApiMode.DIRECT,
                            title     = stringResource(R.string.onboarding_mode_direct_title),
                            subtitle  = stringResource(R.string.onboarding_mode_direct_subtitle),
                            onClick   = { selectedMode = ApiMode.DIRECT },
                        )
                        ModeCard(
                            selected  = selectedMode == ApiMode.SERVER,
                            title     = stringResource(R.string.onboarding_mode_server_title),
                            subtitle  = stringResource(R.string.onboarding_mode_server_subtitle),
                            onClick   = { selectedMode = ApiMode.SERVER },
                        )
                    }

                    if (selectedMode == ApiMode.DIRECT) {
                        OutlinedTextField(
                            value = apiKey, onValueChange = { apiKey = it },
                            label = { Text(stringResource(R.string.onboarding_api_key_label)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.settings_toggle_key_visibility), tint = OnBackground.copy(0.6f))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = scanEatTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Text(stringResource(R.string.onboarding_api_key_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
                    } else {
                        OutlinedTextField(
                            value = serverUrl, onValueChange = { serverUrl = it },
                            label = { Text(stringResource(R.string.settings_server_url)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = scanEatTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    ScanEatPrimaryButton(
                        onClick = {
                            viewModel.setMode(selectedMode)
                            if (apiKey.isNotBlank()) viewModel.setApiKey(apiKey)
                            if (serverUrl.isNotBlank()) viewModel.setServerUrl(serverUrl)
                            page = 3
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = (selectedMode == ApiMode.DIRECT && apiKey.isNotBlank()) ||
                                  (selectedMode == ApiMode.SERVER && serverUrl.isNotBlank()),
                    ) { Text(stringResource(R.string.onboarding_continue_button), fontSize = 16.sp) }
                    TextButton(
                        onClick = { viewModel.skipApiSetup(); page = 3 },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_api_skip), color = OnBackground.copy(0.5f)) }
                }

                // ---- Page 3: Profile prompt ----
                3 -> {
                    Text(stringResource(R.string.onboarding_profile_title), style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.onboarding_profile_body),
                        style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.7f), textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ScanEatPrimaryButton(
                            onClick = { viewModel.finish(); onGoToProfile() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.onboarding_profile_cta)) }
                        TextButton(
                            onClick = { viewModel.finish() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.onboarding_profile_skip), color = OnBackground.copy(0.5f)) }
                    }
                }
            }
        }
    }

}

@Composable
private fun ValueCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    ScanEatCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Icon(icon, null, tint = AccentCoral, modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            }
        }
    }
}

@Composable
private fun ModeCard(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(12.dp),
        color   = if (selected) AccentCoral.copy(0.15f) else SurfaceVariant,
        border  = if (selected) ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.5.dp) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            // onClick = null: the whole Surface above is already clickable (onClick = onClick) —
            // a second independent actionable control nested inside it is a real screen-reader/
            // interaction conflict (two actionable elements claiming the same tap), not just redundant.
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = AccentCoral))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            }
        }
    }
}
