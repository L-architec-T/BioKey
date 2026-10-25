package org.pcbu.libreclient.storage

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * This phone's own persistent identity, sent to the PC as PairInit's
 * deviceUUID/deviceName. Generated once and never changed — the PC's
 * deviceId is itself derived in part from this UUID, so regenerating it
 * would silently break existing pairings.
 */
class DeviceIdentityStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val deviceUUID: String
        get() = prefs.getString(KEY_UUID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_UUID, it).apply()
        }

    var deviceName: String
        get() = prefs.getString(KEY_NAME, null) ?: defaultDeviceName()
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    /**
     * Port this phone's foreground UDP listener binds to (Phase 3), sent as
     * `udpPort` in every PairInit. Fixed after the first pairing: the PC is
     * never told about a later change, so all existing pairings would stop
     * receiving unlock broadcasts.
     */
    var udpListenPort: Int
        get() = prefs.getInt(KEY_UDP_PORT, DEFAULT_UDP_PORT)
        set(value) = prefs.edit().putInt(KEY_UDP_PORT, value).apply()

    /**
     * Current FCM registration token, sent to the PC as PairInit's cloudToken so it
     * can push a wake notification instead of relying solely on the local UDP
     * broadcast. Null until [PcbuFirebaseMessagingService] (or an explicit fetch
     * during pairing) has one.
     */
    var fcmToken: String?
        get() = prefs.getString(KEY_FCM_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_FCM_TOKEN, value).apply()

    private fun defaultDeviceName(): String = Build.MODEL ?: "Android"

    companion object {
        private const val PREFS_NAME = "pcbu_device_identity"
        private const val KEY_UUID = "device_uuid"
        private const val KEY_NAME = "device_name"
        private const val KEY_UDP_PORT = "udp_listen_port"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val DEFAULT_UDP_PORT = 43300
    }
}
