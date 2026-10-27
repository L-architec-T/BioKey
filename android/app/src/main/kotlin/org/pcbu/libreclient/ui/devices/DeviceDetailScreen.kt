package org.pcbu.libreclient.ui.devices

import org.pcbu.libreclient.ui.theme.TextPrimary

import org.pcbu.libreclient.ui.theme.Accent

import androidx.compose.material3.TextField

import org.pcbu.libreclient.storage.db.displayName

import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.Row

import org.pcbu.libreclient.premium.PremiumStar

import org.pcbu.libreclient.premium.PremiumFeature
import org.pcbu.libreclient.premium.PremiumDialog
import org.pcbu.libreclient.premium.PremiumAccess
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.pcbu.libreclient.R
import org.pcbu.libreclient.ui.util.toUserMessage
import org.pcbu.libreclient.ui.appearance.GlassAlertDialog
import org.pcbu.libreclient.ui.components.PillButton
import org.pcbu.libreclient.ui.components.SubScreen
import org.pcbu.libreclient.ui.theme.Red
import org.pcbu.libreclient.ui.theme.TextSecondary
import org.pcbu.libreclient.pairing.PairingRepository
import org.pcbu.libreclient.protocol.model.MacroDto
import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.unlock.LockBroadcastSender

@Composable
fun DeviceDetailScreen(
    pairingRepository: PairingRepository,
    deviceId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onEditMacro: () -> Unit,
) {
    val device by pairingRepository.observeDevice(deviceId).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    val premium = remember { PremiumAccess.get(context) }
    val isPremium by premium.isPremium.collectAsState(initial = true)
    var showPremium by remember { mutableStateOf(false) }

    SubScreen(title = device?.displayName ?: "", onBack = onBack) { padding ->
        val currentDevice = device
        if (currentDevice == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(stringResource(R.string.device_detail_not_found))
            }
            return@SubScreen
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                stringResource(R.string.device_detail_user, currentDevice.userName),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.device_detail_connection, currentDevice.pairingMethod),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                stringResource(R.string.device_detail_os, currentDevice.deviceOS),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Text(
                stringResource(R.string.device_detail_hostname, currentDevice.deviceName),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            PillButton(
                onClick = { showRename = true },
                tone = TextPrimary,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                text = stringResource(R.string.device_detail_rename),
            )

            val suspendSentMessage = stringResource(R.string.device_detail_suspend_sent)
            val suspendErrorMessage = stringResource(R.string.device_detail_suspend_error)
            val suspendNoKeyMessage = stringResource(R.string.device_detail_suspend_no_key)
            PillButton(
                onClick = {
                    scope.launch {
                        status = try {
                            val encKey = pairingRepository.readEncKey(deviceId)
                            if (encKey == null) {
                                suspendNoKeyMessage
                            } else {
                                withContext(Dispatchers.IO) {
                                    LockBroadcastSender.sendSuspend(context, deviceId, encKey)
                                }
                                suspendSentMessage
                            }
                        } catch (e: Exception) {
                            "$suspendErrorMessage : ${e.toUserMessage()}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                text = stringResource(R.string.device_detail_suspend),
            )

            val macroStepCount = remember(currentDevice.macroStepsJson) {
                currentDevice.macroStepsJson?.let {
                    runCatching { PcbuJson.decodeFromString(MacroDto.serializer(), it).steps.size }.getOrNull()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.device_detail_macro_title), style = MaterialTheme.typography.titleMedium)
                PremiumStar()
            }
            Text(
                if (macroStepCount != null && macroStepCount > 0) {
                    stringResource(R.string.device_detail_macro_summary, macroStepCount)
                } else {
                    stringResource(R.string.device_detail_macro_none)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            // deviceOS comes from the pairing response (AppInfo::GetOperatingSystem: "Linux",
            // "Windows" or "macOS"); pcbu-lockd only plays macros on Linux.
            val macrosSupported = currentDevice.deviceOS.equals("Linux", ignoreCase = true)
            Text(
                if (macrosSupported) {
                    stringResource(R.string.macro_timing_notice)
                } else {
                    stringResource(R.string.macro_platform_notice, currentDevice.deviceOS)
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (macrosSupported) PillButton(
                onClick = { if (isPremium) onEditMacro() else showPremium = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                text = stringResource(R.string.device_detail_macro_edit),
            )

            PillButton(
                onClick = { showDeleteConfirm = true },
                tone = Red,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                text = stringResource(R.string.device_detail_delete),
            )

            if (status.isNotEmpty()) {
                Text(status, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }

    if (showPremium) {
        PremiumDialog(PremiumFeature.MACROS, onDismiss = { showPremium = false }, onPurchased = onEditMacro)
    }

    val renamingDevice = device
    if (showRename && renamingDevice != null) {
        var name by remember { mutableStateOf(renamingDevice.customName.orEmpty()) }
        GlassAlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.device_detail_rename)) },
            text = {
                Column {
                    TextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        singleLine = true,
                        placeholder = { Text(renamingDevice.userName.ifBlank { renamingDevice.deviceName }) },
                        label = { Text(stringResource(R.string.device_detail_rename_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.device_detail_rename_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRename = false
                    scope.launch { pairingRepository.updateCustomName(deviceId, name) }
                }) { Text(stringResource(R.string.device_detail_rename_save), color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.cancel), color = TextSecondary) }
            },
        )
    }

    if (showDeleteConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.device_detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.device_detail_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        pairingRepository.deleteDevice(deviceId)
                        onDeleted()
                    }
                }) { Text(stringResource(R.string.device_detail_delete), color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel), color = TextSecondary) }
            },
        )
    }
}
