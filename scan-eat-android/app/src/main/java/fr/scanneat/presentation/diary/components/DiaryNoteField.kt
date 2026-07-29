package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors

@Composable
internal fun DiaryNoteField(noteText: String, onNoteTextChange: (String) -> Unit, isDirty: Boolean, onSave: () -> Unit) {
    OutlinedTextField(
        value = noteText,
        onValueChange = onNoteTextChange,
        label = { Text(stringResource(R.string.diary_note_label)) },
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.S, bottom = Spacing.XS),
        singleLine = false,
        maxLines = 3,
        trailingIcon = {
            if (isDirty) {
                IconButton(onClick = onSave) {
                    Icon(Icons.Rounded.Check, stringResource(R.string.diary_cd_save_note), tint = AccentCoral)
                }
            }
        },
        shape = RoundedCornerShape(CardRadius.CONTROL),
        colors = scanEatTextFieldColors(),
    )
}
