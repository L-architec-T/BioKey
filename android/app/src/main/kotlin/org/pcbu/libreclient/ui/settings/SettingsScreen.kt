package org.pcbu.libreclient.ui.settings

import org.pcbu.libreclient.pairing.PairingRepository
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import android.app.NotificationManager
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import org.pcbu.libreclient.premium.PremiumFeature
import org.pcbu.libreclient.premium.PremiumStar
import org.pcbu.libreclient.premium.PremiumDialog
import org.pcbu.libreclient.premium.PremiumAccess
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.pcbu.libreclient.unlock.BlePresenceAdvertiser
import org.pcbu.libreclient.R
import org.pcbu.libreclient.service.UnlockForegroundService
import org.pcbu.libreclient.storage.DeviceIdentityStore
import org.pcbu.libreclient.storage.PreferencesStore
import org.pcbu.libreclient.ui.components.AccentSwitch
import org.pcbu.libreclient.ui.components.PillButton
import org.pcbu.libreclient.ui.components.SectionCard
import org.pcbu.libreclient.ui.theme.Accent
import org.pcbu.libreclient.ui.theme.AccentDim
import org.pcbu.libreclient.ui.theme.Red
import org.pcbu.libreclient.ui.theme.Success
import org.pcbu.libreclient.ui.theme.TextPrimary
import org.pcbu.libreclient.ui.theme.TextSecondary
import org.pcbu.libreclient.ui.util.requestFullScreenIntentPermission
import org.pcbu.libreclient.ui.util.requestIgnoreBatteryOptimizations

/**
 * "Réglages" tab of the home shell. Appearance (theme, accent, wallpaper, glass) moved to the
 * shared "Apparence" panel from the top-bar menu, so it is no longer duplicated here.
 */
@Composable
fun SettingsScreen(preferencesStore: PreferencesStore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val autoUnlock by preferencesStore.autoUnlockEnabled.collectAsState(initial = false)
    val autoUnlockOnlyIfUnlocked by preferencesStore.autoUnlockOnlyIfPhoneUnlocked.collectAsState(initial = true)
    val directUnlock by preferencesStore.directUnlockEnabled.collectAsState(initial = true)
    val wearableNotification by preferencesStore.wearableNotificationEnabled.collectAsState(initial = false)
    val lockOnWifiDisconnect by preferencesStore.lockOnWifiDisconnectEnabled.collectAsState(initial = false)
    val lockOnBluetooth by preferencesStore.lockOnBluetoothEnabled.collectAsState(initial = false)
    // Android 12+: the beacon needs "Nearby devices"; the option only turns on once granted.
    val advertisePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { preferencesStore.setLockOnBluetoothEnabled(true) }
    }
    val lockTimeoutSeconds by preferencesStore.lockTimeoutSeconds.collectAsState(initial = PreferencesStore.DEFAULT_LOCK_TIMEOUT_SECONDS)
    val selectUserOnWifiConnect by preferencesStore.selectUserOnWifiConnectEnabled.collectAsState(initial = false)
    val biometricEncryption by preferencesStore.biometricEncryptionEnabled.collectAsState(initial = false)
    val pairingRepository = remember { PairingRepository(context) }
    val serviceRunning by UnlockForegroundService.isRunning.collectAsState()
    val premium = remember { PremiumAccess.get(context) }
    val isPremium by premium.isPremium.collectAsState(initial = true)
    var premiumDialog by remember { mutableStateOf<PremiumFeature?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (premium.canPurchase) {
            SectionCard("BioKey Premium") {
                Text(
                    if (isPremium) "Premium actif : plusieurs PC, verrouillage de proximité et macros."
                    else "Plusieurs PC, verrouillage de proximité et macros pour ${PremiumAccess.PRICE_LABEL}, à vie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (!isPremium) {
                    PillButton(
                        text = "Passer à Premium",
                        onClick = { premiumDialog = PremiumFeature.NONE },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                if (premium.isSimulated) {
                    SettingsSwitchRow(
                        title = "Premium simulé (test)",
                        subtitle = "Version de test uniquement : active ou retire l'achat sans payer.",
                        checked = isPremium,
                        onCheckedChange = { scope.launch { premium.setSimulatedPremium(it) } },
                    )
                }
            }
        }

        SectionCard(stringResource(R.string.settings_section_unlocking)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_direct_unlock),
                subtitle = stringResource(R.string.settings_direct_unlock_desc),
                checked = directUnlock,
                onCheckedChange = {
                    scope.launch { preferencesStore.setDirectUnlockEnabled(it) }
                    if (it) requestFullScreenIntentPermission(context)
                },
            )
            if (directUnlock) DirectUnlockPermissions()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_auto_unlock),
                subtitle = stringResource(R.string.settings_auto_unlock_warning),
                checked = autoUnlock,
                onCheckedChange = { scope.launch { preferencesStore.setAutoUnlockEnabled(it) } },
            )
            if (autoUnlock) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_unlock_only_if_phone_unlocked),
                    checked = autoUnlockOnlyIfUnlocked,
                    onCheckedChange = { scope.launch { preferencesStore.setAutoUnlockOnlyIfPhoneUnlocked(it) } },
                )
            }
            SettingsSwitchRow(
                title = stringResource(R.string.settings_wearable_notification),
                subtitle = stringResource(R.string.settings_wearable_notification_desc),
                checked = wearableNotification,
                onCheckedChange = { scope.launch { preferencesStore.setWearableNotificationEnabled(it) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_lock_on_wifi_disconnect),
                subtitle = stringResource(R.string.settings_lock_on_wifi_disconnect_desc),
                premium = true,
                checked = lockOnWifiDisconnect && isPremium,
                onCheckedChange = {
                    if (!isPremium) premiumDialog = PremiumFeature.PROXIMITY_LOCK
                    else scope.launch { preferencesStore.setLockOnWifiDisconnectEnabled(it) }
                },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_lock_on_bluetooth),
                subtitle = stringResource(R.string.settings_lock_on_bluetooth_desc),
                premium = true,
                checked = lockOnBluetooth && isPremium,
                onCheckedChange = { enabled ->
                    if (!isPremium) {
                        premiumDialog = PremiumFeature.PROXIMITY_LOCK
                    } else if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !BlePresenceAdvertiser(context).hasPermission()) {
                        advertisePermissionLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
                    } else {
                        scope.launch { preferencesStore.setLockOnBluetoothEnabled(enabled) }
                    }
                },
            )
            if (isPremium && (lockOnWifiDisconnect || lockOnBluetooth)) {
                Text(stringResource(R.string.settings_lock_timeout), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferencesStore.LOCK_TIMEOUT_CHOICES_SECONDS.forEach { seconds ->
                        FilterChip(
                            selected = seconds == lockTimeoutSeconds,
                            onClick = { scope.launch { preferencesStore.setLockTimeoutSeconds(seconds) } },
                            label = { Text(if (seconds < 60) "$seconds s" else "${seconds / 60} min") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentDim,
                                selectedLabelColor = Accent,
                                labelColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
            SettingsSwitchRow(
                title = stringResource(R.string.settings_select_user_on_wifi_connect),
                subtitle = stringResource(R.string.settings_select_user_on_wifi_connect_desc),
                checked = selectUserOnWifiConnect,
                onCheckedChange = { scope.launch { preferencesStore.setSelectUserOnWifiConnectEnabled(it) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_biometric_encryption),
                subtitle = stringResource(R.string.settings_biometric_encryption_desc),
                checked = biometricEncryption,
                onCheckedChange = { scope.launch { pairingRepository.setBiometricEncryption(it) } },
            )
        }

        SectionCard(stringResource(R.string.settings_section_service)) {
            Text(
                stringResource(R.string.settings_service_port, DeviceIdentityStore(context).udpListenPort),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            PillButton(
                text = stringResource(if (serviceRunning) R.string.settings_service_stop else R.string.settings_service_start),
                tone = if (serviceRunning) Red else Success,
                onClick = {
                    if (serviceRunning) {
                        UnlockForegroundService.stop(context)
                    } else {
                        // Requested here, not via standalone Settings buttons: this is the
                        // one moment the app actually needs them, and each request is a
                        // no-op if already granted (see the two helpers).
                        requestIgnoreBatteryOptimizations(context)
                        requestFullScreenIntentPermission(context)
                        UnlockForegroundService.start(context)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }

    premiumDialog?.let { feature -> PremiumDialog(feature, onDismiss = { premiumDialog = null }) }
}

/** What still keeps "Déverrouillage direct" from opening the prompt by itself, re-checked each
 * time the user comes back from the system settings. */
@Composable
private fun DirectUnlockPermissions() {
    val context = LocalContext.current
    var fullScreenOk by remember { mutableStateOf(true) }
    var overlayOk by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        fullScreenOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() != false
        overlayOk = Settings.canDrawOverlays(context)
        onPauseOrDispose {}
    }
    if (!fullScreenOk) PermissionHint(stringResource(R.string.settings_direct_unlock_fullscreen_missing)) {
        requestFullScreenIntentPermission(context)
    }
    if (!overlayOk) PermissionHint(stringResource(R.string.settings_direct_unlock_overlay_missing)) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
    }
}

@Composable
private fun PermissionHint(text: String, onAllow: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
        TextButton(onClick = onAllow) {
            Text(stringResource(R.string.settings_direct_unlock_allow), color = Accent)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    premium: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                if (premium) PremiumStar()
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        AccentSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
