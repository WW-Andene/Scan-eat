package fr.scanneat.presentation.scan

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Camera hardware/permission lifecycle for ScanScreen — extracted verbatim
 * (§T1 composition-root split) so ScanScreen's own body reads as pure layout
 * dispatch. See individual field doc comments for the non-obvious behavior
 * each one guards against; they're unchanged from ScanScreen's prior inline
 * version. hasCamera/cameraUnavailable/permanentlyDenied are the live
 * MutableState instances themselves (single source of truth) so ScanScreen
 * can still read and write them directly (e.g. CameraPreview's onCameraError
 * callback sets cameraUnavailable.value = true).
 */
class CameraPermissionState internal constructor(
    val hasCameraHardware: Boolean,
    val hasCamera: MutableState<Boolean>,
    val cameraUnavailable: MutableState<Boolean>,
    val permanentlyDenied: MutableState<Boolean>,
    val requestPermission: () -> Unit,
)

@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // android:required="false" on both camera <uses-feature> entries in the manifest
    // (see AndroidManifest.xml) tells the Play Store this app installs fine on devices
    // with no camera at all (some tablets/Chromebooks/emulators). Requesting the CAMERA
    // *permission* on such a device still "succeeds" trivially - there's simply no
    // hardware behind it - so hasCamera below would stay true forever while
    // CameraPreview's bindToLifecycle silently fails every time. Checking the actual
    // hardware feature up front lets these devices skip straight to a usable fallback
    // instead of a dead permission prompt.
    val hasCameraHardware = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    val hasCamera = remember {
        mutableStateOf(
            hasCameraHardware &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    // Set when CameraPreview's own bindToLifecycle call fails (e.g. camera held by
    // another app, or a hardware/driver fault) - previously swallowed by a bare
    // runCatching with no onFailure branch, leaving a permanently blank preview and a
    // capture button that silently did nothing, with zero feedback for the user.
    val cameraUnavailable = remember { mutableStateOf(false) }
    // Once the user permanently denies (checked "don't ask again", or a 2nd
    // straight denial on API 30+), RequestPermission() silently returns false
    // without even showing the system dialog again — "Autoriser" would look
    // broken forever with no way to reach the scanner, the app's core
    // feature. Track a request having already happened once, so a denial
    // with no rationale available next time is recognized as permanent.
    var requestedOnce by remember { mutableStateOf(false) }
    val permanentlyDenied = remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera.value = granted
        if (!granted) {
            val activity = context as? Activity
            val canShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: true
            if (requestedOnce && !canShowRationale) permanentlyDenied.value = true
        }
        requestedOnce = true
    }

    // hasCamera was only ever updated by the permission-request launcher's own
    // callback - revoking Camera permission from system Settings while this
    // screen is backgrounded left it stuck true, so CameraPreview's next bind
    // attempt threw a SecurityException that got misclassified as a hardware/
    // driver fault (cameraUnavailable=true), trapping the user in a "Retry"
    // loop that could never succeed since the real problem (missing permission)
    // was never rechecked and the permission-request UI was never re-shown.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasCameraHardware &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted != hasCamera.value) {
                    hasCamera.value = granted
                    cameraUnavailable.value = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember {
        CameraPermissionState(
            hasCameraHardware = hasCameraHardware,
            hasCamera = hasCamera,
            cameraUnavailable = cameraUnavailable,
            permanentlyDenied = permanentlyDenied,
            requestPermission = { permLauncher.launch(Manifest.permission.CAMERA) },
        )
    }
}
