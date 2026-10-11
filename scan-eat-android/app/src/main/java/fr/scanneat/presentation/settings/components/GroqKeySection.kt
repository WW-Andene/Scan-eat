package fr.scanneat.presentation.settings.components

import androidx.compose.runtime.Composable
import fr.scanneat.R

@Composable
internal fun GroqKeySection(
    localKey: String, onLocalKeyChange: (String) -> Unit,
    keyVisible: Boolean, onToggleVisible: () -> Unit,
    saved: Boolean, onSave: () -> Unit,
) {
    ApiKeyInputSection(
        titleRes = R.string.settings_section_groq_key,
        fieldLabelRes = R.string.settings_groq_key_placeholder,
        localKey = localKey, onLocalKeyChange = onLocalKeyChange,
        keyVisible = keyVisible, onToggleVisible = onToggleVisible,
        saved = saved, onSave = onSave,
        hintAfterRes = R.string.onboarding_api_key_hint,
    )
}
