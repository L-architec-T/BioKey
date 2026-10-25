package org.pcbu.libreclient.ui.pairing

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import org.pcbu.libreclient.R
import org.pcbu.libreclient.ui.util.toUserMessage
import org.pcbu.libreclient.ui.components.PillButton
import org.pcbu.libreclient.ui.components.SubScreen
import org.pcbu.libreclient.ui.theme.TextSecondary
import org.pcbu.libreclient.pairing.PairingRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScanScreen(
    pairingRepository: PairingRepository,
    onPaired: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var qrInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val cancelledMessage = stringResource(R.string.pairing_scan_cancelled)
    val cameraDeniedMessage = stringResource(R.string.pairing_scan_camera_denied)
    val errorPrefix = stringResource(R.string.pairing_scan_error)

    fun pair(rawQr: String) {
        scope.launch {
            status = try {
                pairingRepository.pairFromQr(rawQr)
                onPaired()
                ""
            } catch (e: Exception) {
                "$errorPrefix : ${e.toUserMessage()}"
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw != null) pair(raw) else status = cancelledMessage
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setOrientationLocked(false)
                    .setBeepEnabled(false),
            )
        } else {
            status = cameraDeniedMessage
        }
    }

    SubScreen(title = stringResource(R.string.pairing_scan_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.pairing_scan_instructions), color = TextSecondary)

            PillButton(
                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                text = stringResource(R.string.pairing_scan_button),
            )

            TextField(
                value = qrInput,
                onValueChange = { qrInput = it },
                label = { Text(stringResource(R.string.pairing_scan_manual_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                shape = RoundedCornerShape(14.dp),
            )
            PillButton(
                onClick = { pair(qrInput) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                text = stringResource(R.string.pairing_scan_manual_button),
            )

            if (status.isNotEmpty()) {
                Text(status, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
