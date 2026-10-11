package fr.scanneat.presentation.recipes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecipesPhotoLaunchers internal constructor(
    val photoImportLaunch: () -> Unit,
)

private fun launchPicker(launcher: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>) {
    launcher.launch(
        PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build(),
    )
}

/** RecipesScreen's recipe-photo picker: system photo picker -> decode off Main -> hand to the ViewModel. */
@Composable
fun rememberRecipesPhotoLaunchers(
    onRecipePhotos: (List<fr.scanneat.data.remote.api.ImagePayload>) -> Unit,
    onDecodeFailed: () -> Unit,
): RecipesPhotoLaunchers {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    fun handlePicked(uri: Uri?, scope: CoroutineScope, onPayload: (List<fr.scanneat.data.remote.api.ImagePayload>) -> Unit) {
        if (uri == null) return
        scope.launch {
            val payload = withContext(Dispatchers.IO) { decodeImagePayload(context, uri) }
            if (payload != null) onPayload(listOf(payload)) else onDecodeFailed()
        }
    }

    val photoImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        handlePicked(uri, coroutineScope, onRecipePhotos)
    }

    return RecipesPhotoLaunchers(
        photoImportLaunch = { launchPicker(photoImportLauncher) },
    )
}
