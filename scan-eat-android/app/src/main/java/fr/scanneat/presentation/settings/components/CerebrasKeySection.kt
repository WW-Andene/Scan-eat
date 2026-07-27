package fr.scanneat.presentation.settings.components

import androidx.compose.runtime.Composable
import fr.scanneat.R

@Composable
internal fun CerebrasKeySection(
    localCerebrasKey: String, onLocalCerebrasKeyChange: (String) -> Unit,
    cerebrasKeyVisible: Boolean, onToggleVisible: () -> Unit,
    saved: Boolean, onSave: () -> Unit,
) {
    ApiKeyInputSection(
        titleRes = R.string.settings_section_cerebras_key,
        fieldLabelRes = R.string.settings_cerebras_key_placeholder,
        localKey = localCerebrasKey, onLocalKeyChange = onLocalCerebrasKeyChange,
        keyVisible = cerebrasKeyVisible, onToggleVisible = onToggleVisible,
        saved = saved, onSave = onSave,
        hintBeforeRes = R.string.settings_cerebras_key_hint,
    )
}
