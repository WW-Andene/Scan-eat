package fr.scanneat.presentation.profileshare

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import fr.scanneat.R
import fr.scanneat.presentation.scan.components.CameraPreview
import fr.scanneat.presentation.scan.rememberCameraPermissionState
import fr.scanneat.presentation.ui.theme.*

internal fun qrBitmap(text: String, size: Int = 768): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
        for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    }
}

/** Roadmap N8: show the active profile's allergens/diet as an encrypted QR + code. */
@Composable
fun ProfileShareScreen(onBack: () -> Unit, viewModel: ProfileShareViewModel = hiltViewModel()) {
    val out = viewModel.outgoing.collectAsStateWithLifecycle().value
    LaunchedEffect(Unit) { viewModel.prepareShare() }
    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.profile_share_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.L), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Text(stringResource(R.string.profile_share_body), style = MaterialTheme.typography.bodySmall, color = OnBackground)
            if (out == null) CircularProgressIndicator(color = AccentCoral) else {
                val bmp = remember(out.qrText) { qrBitmap(out.qrText) }
                Image(bmp.asImageBitmap(), stringResource(R.string.profile_share_title), Modifier.size(256.dp))
                Text(stringResource(R.string.profile_share_code), style = MaterialTheme.typography.labelMedium, color = OnBackground)
                Text(out.code.chunked(4).joinToString(" "), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = OnBackground)
            }
        }
    }
}

/** Roadmap N8: scan a carer QR and type its code - creates a new local profile. */
@Composable
fun ProfileImportScreen(onBack: () -> Unit, viewModel: ProfileShareViewModel = hiltViewModel()) {
    val s = viewModel.import.collectAsStateWithLifecycle().value
    val cameraPermission = rememberCameraPermissionState()
    val hasCamera by cameraPermission.hasCamera
    val receivedLabel = stringResource(R.string.profile_share_received)
    Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
        if (s is ProfileShareViewModel.Import.Scanning) {
            if (hasCamera) CameraPreview(onBarcodeDetected = {}, onPhotoCaptured = {}, onRawQrDetected = viewModel::onQr)
            else Button(onClick = cameraPermission.requestPermission, modifier = Modifier.align(Alignment.Center)) { Text(stringResource(R.string.receipt_scan_grant_permission)) }
            Row(Modifier.padding(Spacing.M).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) }
                Text(stringResource(R.string.profile_import_hint), color = OnBackground)
            }
        } else Column(Modifier.fillMaxSize().padding(Spacing.L), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            when (s) {
                is ProfileShareViewModel.Import.NeedCode -> {
                    var code by remember { mutableStateOf("") }
                    Text(stringResource(R.string.profile_import_code), color = OnBackground)
                    OutlinedTextField(code, { code = it }, singleLine = true, isError = s.wrongCode,
                        supportingText = { if (s.wrongCode) Text(stringResource(R.string.profile_import_wrong_code)) })
                    Button(onClick = { viewModel.submitCode(code, receivedLabel) }, enabled = code.isNotBlank()) { Text(stringResource(R.string.profile_import_confirm)) }
                }
                is ProfileShareViewModel.Import.Working -> CircularProgressIndicator(color = AccentCoral)
                is ProfileShareViewModel.Import.Done -> {
                    Text(stringResource(R.string.profile_import_done, s.name), color = OnBackground)
                    Button(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                }
                else -> {}
            }
        }
    }
}
