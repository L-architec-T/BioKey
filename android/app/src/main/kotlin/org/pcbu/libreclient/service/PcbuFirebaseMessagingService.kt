package org.pcbu.libreclient.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.pcbu.libreclient.protocol.model.UdpBroadcast
import org.pcbu.libreclient.storage.DeviceIdentityStore

/**
 * Reliable alternative to the UDP wake broadcast: a phone's Wi-Fi radio can sit
 * in a reduced-RX-duty-cycle sleep state that silently drops incoming UDP
 * packets even while this app holds a wake lock + Wi-Fi lock (see
 * [UnlockForegroundService]) — FCM's data connection is exempt from that,
 * since it rides Play Services' own persistent, system-privileged channel.
 *
 * The push payload mirrors [UdpBroadcast] exactly; the actual encrypted
 * challenge/response still happens over a direct TCP connection to the PC,
 * FCM only replaces the "someone wants to talk" nudge.
 */
class PcbuFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val deviceId = data["deviceId"] ?: return
        val pcbuIP = data["pcbuIP"] ?: return
        val pcbuPort = data["pcbuPort"]?.toIntOrNull() ?: return
        val isManual = data["isManual"]?.toBooleanStrictOrNull() ?: false
        val wakeId = data["wakeId"] ?: ""
        UnlockForegroundService.wakeFromRemote(
            this,
            UdpBroadcast(deviceId = deviceId, pcbuIP = pcbuIP, pcbuPort = pcbuPort, isManual = isManual, wakeId = wakeId),
        )
    }

    /**
     * FCM tokens are stable in practice (they only rotate on app reinstall, data
     * wipe, or a rare Play Services-initiated refresh) and are handed to a PC once,
     * during pairing (see PairInit.cloudToken). A rotation after pairing isn't
     * pushed to already-paired PCs — re-pairing picks up the new token. Storing it
     * locally here just makes sure the *next* pairing (or re-pairing) always sends
     * the current one.
     */
    override fun onNewToken(token: String) {
        DeviceIdentityStore(this).fcmToken = token
    }
}
