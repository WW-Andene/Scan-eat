package fr.scanneat.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.presentation.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    isTabRoot: Boolean = false,
    onOpenProfile: () -> Unit = {},
) {
    val apiKey    = viewModel.apiKey.collectAsStateWithLifecycle()
    val mode      = viewModel.mode.collectAsStateWithLifecycle()
    val serverUrl = viewModel.serverUrl.collectAsStateWithLifecycle()
    val language  = viewModel.language.collectAsStateWithLifecycle()
    val theme     = viewModel.theme.collectAsStateWithLifecycle()

    var keyVisible  by remember { mutableStateOf(false) }
    var localKey    by remember(apiKey.value)    { mutableStateOf(apiKey.value) }
    var localUrl    by remember(serverUrl.value) { mutableStateOf(serverUrl.value) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres", color = OnBackground) },
                navigationIcon = if (isTabRoot) {} else { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour", tint = OnBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ---- API Mode ----
            SettingsSection("Mode API") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApiMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode.value == m,
                            onClick  = { viewModel.setMode(m) },
                            label    = { Text(if (m == ApiMode.DIRECT) "Direct" else "Serveur") },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen,
                            ),
                        )
                    }
                }
                Text(
                    if (mode.value == ApiMode.DIRECT) "Appelle Groq directement avec votre clé."
                    else "Appelle votre backend Ktor à l'URL ci-dessous.",
                    style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
                )
            }

            // ---- Groq API key ----
            if (mode.value == ApiMode.DIRECT) {
                SettingsSection("Clé Groq") {
                    OutlinedTextField(
                        value = localKey, onValueChange = { localKey = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("gsk_…") },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = OnBackground.copy(0.6f))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                    )
                    Text("Clé gratuite sur console.groq.com", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
                    Button(onClick = { viewModel.saveApiKey(localKey) }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp)) {
                        Text("Sauvegarder", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ---- Server URL ----
            if (mode.value == ApiMode.SERVER) {
                SettingsSection("URL du serveur") {
                    OutlinedTextField(
                        value = localUrl, onValueChange = { localUrl = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("https://…") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                    )
                    Button(onClick = { viewModel.saveServerUrl(localUrl) }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp)) {
                        Text("Sauvegarder", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Fix 4: Language toggle
            SettingsSection("Langue") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("fr" to "Français", "en" to "English").forEach { (code, label) ->
                        FilterChip(
                            selected = language.value == code,
                            onClick  = { viewModel.setLanguage(code) },
                            label    = { Text(label) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen,
                            ),
                        )
                    }
                }
            }

            // Fix 4: Theme toggle
            SettingsSection("Thème") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("oled" to "OLED", "dark" to "Sombre", "light" to "Clair").forEach { (key, label) ->
                        FilterChip(
                            selected = theme.value == key,
                            onClick  = { viewModel.setTheme(key) },
                            label    = { Text(label) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen,
                            ),
                        )
                    }
                }
            }

            // Profile shortcut
            SettingsSection("Profil nutritionnel") {
                Text("Sexe, âge, taille, poids, régime, allergènes — requis pour le score personnalisé.", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                Button(
                    onClick = onOpenProfile,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Person, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Modifier mon profil", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }

            // About
            SettingsSection("À propos") {
                Text("Scan'eat v0.1.0 · Moteur de scoring v2.2.0", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                Text("minSdk 26 · Kotlin + Jetpack Compose", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
            }

            Spacer(Modifier.height(40.dp))
        }
    }

}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
        content()
    }
}
