package org.pcbu.libreclient.pairing

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.pcbu.libreclient.net.LocalNetworkInfo
import org.pcbu.libreclient.storage.DeviceIdentityStore
import org.pcbu.libreclient.storage.PreferencesStore
import org.pcbu.libreclient.storage.SecretsStore
import org.pcbu.libreclient.storage.db.PairedDeviceEntity
import org.pcbu.libreclient.storage.db.PcbuDatabase

/**
 * Android-side glue: runs :core's pure network/crypto PairingClient, then
 * splits the result between Room (non-secret metadata) and SecretsStore
 * (Keystore-wrapped encKey/passwordKey).
 */
class PairingRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = PcbuDatabase.getInstance(appContext)
    private val secrets = SecretsStore(appContext)
    private val identity = DeviceIdentityStore(appContext)
    private val preferences = PreferencesStore(appContext)

    suspend fun pairFromQr(rawQr: String): PairedDeviceRecord = withContext(Dispatchers.IO) {
        val thisDevice = ThisDeviceInfo(
            deviceUUID = identity.deviceUUID,
            deviceName = identity.deviceName,
            ipAddress = LocalNetworkInfo.findLocalIPv4Address() ?: "0.0.0.0",
            udpPort = identity.udpListenPort,
            cloudToken = fcmToken(),
        )

        val record = PairingClient.pairFromQr(rawQr, thisDevice)
        persist(record)
        record
    }

    /** [PcbuFirebaseMessagingService.onNewToken] caches this after the first fetch;
     * on a first-ever pairing nothing has triggered that yet, so fetch directly. */
    private fun fcmToken(): String =
        identity.fcmToken ?: runCatching { Tasks.await(FirebaseMessaging.getInstance().token) }
            .onSuccess { identity.fcmToken = it }
            .getOrNull()
            ?: ""

    private suspend fun persist(record: PairedDeviceRecord) {
        db.pairedDeviceDao().insert(
            PairedDeviceEntity(
                deviceId = record.deviceId,
                deviceName = record.deviceName,
                deviceOS = record.deviceOS,
                userName = record.userName,
                unlockServerPort = record.unlockServerPort,
                macAddressesCsv = record.macAddresses.joinToString(","),
                pairingMethod = record.pairingMethod,
                pairedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        secrets.storeEncKey(record.deviceId, record.encKey)
        secrets.storePasswordKey(record.deviceId, record.passwordKey, useBiometricGate = preferences.biometricEncryptionEnabled.first())
    }

    suspend fun deleteDevice(deviceId: String) = withContext(Dispatchers.IO) {
        db.pairedDeviceDao().deleteById(deviceId)
        secrets.deleteDevice(deviceId)
    }

    /** Local persistence only — pushing the macro to pcbu-lockd is a separate,
     * best-effort step (see LockBroadcastSender.sendMacro), same split as every
     * other remote command in this app. */
    suspend fun updateMacroSteps(deviceId: String, macroStepsJson: String?) = withContext(Dispatchers.IO) {
        db.pairedDeviceDao().updateMacroSteps(deviceId, macroStepsJson)
    }

    /** Applies "Chiffrement biométrique" to the PCs already paired. Turning it on gates every
     * passwordKey now; turning it off can't decrypt them without a fingerprint, so each one is
     * stored plain again on its next unlock instead (see UnlockForegroundService). */
    suspend fun setBiometricEncryption(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferences.setBiometricEncryptionEnabled(enabled)
        if (enabled) db.pairedDeviceDao().getAll().forEach { secrets.gatePasswordKeyWithBiometric(it.deviceId) }
    }

    fun observeDevices() = db.pairedDeviceDao().observeAll()

    fun observeDevice(deviceId: String) = db.pairedDeviceDao().observeById(deviceId)

    /** For remote commands sent directly from a UI screen (e.g. suspend) rather than
     * through [UnlockForegroundService], which already holds its own SecretsStore. */
    fun readEncKey(deviceId: String): String? = secrets.readEncKey(deviceId)
}
