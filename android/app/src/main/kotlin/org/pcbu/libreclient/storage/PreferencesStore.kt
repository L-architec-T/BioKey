package org.pcbu.libreclient.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pcbu_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * User-facing settings (Settings > Appearance/Unlocking/Service). Distinct
 * from [DeviceIdentityStore] (this phone's protocol identity, never
 * user-editable after first pairing) and [SecretsStore] (per-device crypto
 * material). Auto-unlock defaults strictly match pcbu-desktop's own
 * defaults: both OFF, since skipping the biometric prompt trades security
 * for convenience.
 */
class PreferencesStore(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }
    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }
    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    val autoUnlockEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_UNLOCK] ?: false }
    suspend fun setAutoUnlockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_UNLOCK] = enabled }
    }

    val autoUnlockOnlyIfPhoneUnlocked: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_UNLOCK_IF_UNLOCKED] ?: true }
    suspend fun setAutoUnlockOnlyIfPhoneUnlocked(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_UNLOCK_IF_UNLOCKED] = enabled }
    }

    /** Off: a plain heads-up notification the user must tap before the biometric
     * prompt appears. On (default, matching pcbu-desktop's own default): the
     * prompt shows immediately via a full-screen intent. */
    val directUnlockEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_DIRECT_UNLOCK] ?: true }
    suspend fun setDirectUnlockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DIRECT_UNLOCK] = enabled }
    }

    /** Adds Approve/Deny actions to the challenge notification so it stays usable once
     * bridged to a paired Wear OS watch, which can't open a phone Activity or run
     * BiometricPrompt. Off by default, matching pcbu-desktop's own default. */
    val wearableNotificationEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_WEARABLE_NOTIFICATION] ?: false }
    suspend fun setWearableNotificationEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_WEARABLE_NOTIFICATION] = enabled }
    }

    /** Off by default, matching pcbu-desktop's own "Biometric encryption" toggle: wraps the
     * passwordKey of PCs paired from then on behind a strong-biometric Keystore key (see
     * SecretsStore). Already-paired PCs keep their storage until re-paired. */
    val biometricEncryptionEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_BIOMETRIC_ENCRYPTION] ?: false }
    suspend fun setBiometricEncryptionEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC_ENCRYPTION] = enabled }
    }

    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }
    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = complete }
    }

    /** Proximity lock, off by default: locking every paired PC whenever this phone goes out of
     * range is a meaningful behavior change a user should opt into. The phone sends presence
     * heartbeats; pcbu-lockd does the locking once they stop for [lockTimeoutSeconds]. */
    val lockOnWifiDisconnectEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_LOCK_ON_WIFI_DISCONNECT] ?: false }
    suspend fun setLockOnWifiDisconnectEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LOCK_ON_WIFI_DISCONNECT] = enabled }
    }

    /** Bluetooth flavour of the proximity lock, independent of [lockOnWifiDisconnectEnabled]
     * and sharing [lockTimeoutSeconds]: the phone advertises a signed BLE beacon and pcbu-lockd
     * locks once it stops hearing it. Off by default for the same reason. */
    val lockOnBluetoothEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_LOCK_ON_BLUETOOTH] ?: false }
    suspend fun setLockOnBluetoothEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LOCK_ON_BLUETOOTH] = enabled }
    }

    val lockTimeoutSeconds: Flow<Int> = dataStore.data.map { it[KEY_LOCK_TIMEOUT_SECONDS] ?: DEFAULT_LOCK_TIMEOUT_SECONDS }
    suspend fun setLockTimeoutSeconds(seconds: Int) {
        dataStore.edit { it[KEY_LOCK_TIMEOUT_SECONDS] = seconds }
    }

    /** Off by default, same reasoning as [lockOnWifiDisconnectEnabled]: a symmetric opt-in
     * for the reverse case, sending a "selectUser" command (see LockBroadcastSender) to
     * every paired PC so a multi-account GDM/SDDM/etc. greeter is already showing this
     * device's user's password prompt by the time its normal UDP/FCM unlock wake follows.
     * Fired when this phone (re)joins Wi-Fi (see UnlockForegroundService.selectUserOnAllPairedDevices). */
    val selectUserOnWifiConnectEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_SELECT_USER_ON_WIFI_CONNECT] ?: false }
    suspend fun setSelectUserOnWifiConnectEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SELECT_USER_ON_WIFI_CONNECT] = enabled }
    }

    /** Home stats card: unlock challenges answered with success / refused (denied, cancelled or
     * timed out), counted on this phone since install. */
    val unlockSuccessCount: Flow<Int> = dataStore.data.map { it[KEY_UNLOCK_SUCCESS_COUNT] ?: 0 }
    val unlockDeniedCount: Flow<Int> = dataStore.data.map { it[KEY_UNLOCK_DENIED_COUNT] ?: 0 }
    suspend fun incrementUnlockSuccess() {
        dataStore.edit { it[KEY_UNLOCK_SUCCESS_COUNT] = (it[KEY_UNLOCK_SUCCESS_COUNT] ?: 0) + 1 }
    }
    suspend fun incrementUnlockDenied() {
        dataStore.edit { it[KEY_UNLOCK_DENIED_COUNT] = (it[KEY_UNLOCK_DENIED_COUNT] ?: 0) + 1 }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_AUTO_UNLOCK = booleanPreferencesKey("auto_unlock_enabled")
        private val KEY_AUTO_UNLOCK_IF_UNLOCKED = booleanPreferencesKey("auto_unlock_only_if_phone_unlocked")
        private val KEY_DIRECT_UNLOCK = booleanPreferencesKey("direct_unlock_enabled")
        private val KEY_WEARABLE_NOTIFICATION = booleanPreferencesKey("wearable_notification_enabled")
        private val KEY_BIOMETRIC_ENCRYPTION = booleanPreferencesKey("biometric_encryption_enabled")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_LOCK_ON_WIFI_DISCONNECT = booleanPreferencesKey("lock_on_wifi_disconnect_enabled")
        private val KEY_LOCK_ON_BLUETOOTH = booleanPreferencesKey("lock_on_bluetooth_enabled")
        private val KEY_LOCK_TIMEOUT_SECONDS = intPreferencesKey("lock_timeout_seconds")
        const val DEFAULT_LOCK_TIMEOUT_SECONDS = 60
        val LOCK_TIMEOUT_CHOICES_SECONDS = listOf(30, 60, 120, 300)
        private val KEY_SELECT_USER_ON_WIFI_CONNECT = booleanPreferencesKey("select_user_on_wifi_connect_enabled")
        private val KEY_UNLOCK_SUCCESS_COUNT = intPreferencesKey("unlock_success_count")
        private val KEY_UNLOCK_DENIED_COUNT = intPreferencesKey("unlock_denied_count")
    }
}
