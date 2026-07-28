package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ServerUrlSection(localUrl: String, onLocalUrlChange: (String) -> Unit, saved: Boolean, onSave: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_server_url), icon = Icons.Default.Dns) {
        OutlinedTextField(
            value = localUrl, onValueChange = onLocalUrlChange,
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_server_url_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true, shape = RoundedCornerShape(CardRadius.CONTROL),
            colors = scanEatTextFieldColors(),
        )
        SaveButtonRow(saved = saved, onSave = onSave)
    }
}
