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
    val menuScanLaunch: () -> Unit,
)

private fun launchPicker(launcher: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>) {
    launcher.launch(
        PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build(),
    )
}

/**
 * RecipesScreen's two photo-picker entry points — recipe-photo import and
 * restaurant-menu scan (IdentifyMenuRoute.kt). Both follow the identical
 * "system photo picker -> decode off Main -> hand to ViewModel" shape;
 * extracted together (§T1 composition-root split) rather than duplicated
 * inline twice in the screen body.
 */
@Composable
fun rememberRecipesPhotoLaunchers(
    onRecipePhotos: (List<fr.scanneat.data.remote.api.ImagePayload>) -> Unit,
    onMenuPhotos: (List<fr.scanneat.data.remote.api.ImagePayload>) -> Unit,
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
    val menuScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        handlePicked(uri, coroutineScope, onMenuPhotos)
    }

    return RecipesPhotoLaunchers(
        photoImportLaunch = { launchPicker(photoImportLauncher) },
        menuScanLaunch = { launchPicker(menuScanLauncher) },
    )
}
