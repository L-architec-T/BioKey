package org.pcbu.libreclient.unlock

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import org.pcbu.libreclient.crypto.AesGcmPacketCodec
import org.pcbu.libreclient.crypto.HexCodec
import org.pcbu.libreclient.protocol.model.LockRequest
import org.pcbu.libreclient.protocol.model.PcbuJson

/**
 * The reverse of [UnlockUdpListener]/UDPUnlockBroadcaster: instead of the PC nudging the
 * phone, this nudges the PC's pcbu-lockd. Broadcast (not unicast) because, unlike an
 * incoming wake, the phone never learns the PC's current IP on its own — it only ever
 * sees it transiently inside a PC-initiated [org.pcbu.libreclient.protocol.model.UdpBroadcast].
 * A subnet-directed broadcast (computed from DHCP info) is tried first since some routers
 * drop the limited 255.255.255.255 broadcast; that address is still sent as a fallback.
 *
 * The plaintext payload carries no command — [AesGcmPacketCodec] decrypting successfully
 * under the paired device's own key (plus its timestamp anti-replay window) is itself the
 * whole authorization, same trust model as every other PCBU packet. `cmd` on the wire
 * (see [LockRequest]) is what tells pcbu-lockd which of its remote commands to run.
 */
object LockBroadcastSender {
    const val LOCK_LISTEN_PORT = 43298
    private const val LOCK_MARKER = "lock"
    private const val SELECT_USER_MARKER = "selectUser"
    private const val SUSPEND_MARKER = "suspend"
    private const val PRESENCE_OFF_MARKER = "presenceOff"

    fun send(context: Context, deviceId: String, encKey: String) =
        sendCommand(context, deviceId, encKey, cmd = "lock", marker = LOCK_MARKER)

    /** Pushes the post-unlock macro configured on DeviceDetailScreen/MacroEditorScreen to
     * pcbu-lockd, which stores it against this device and later replays it itself once it
     * independently detects the session actually unlocked (see Login1UnlockWatcher) — this
     * call has no connection to the moment of any actual unlock, it's a config-sync action.
     * Unlike the other markers here, [macroJson] IS the payload pcbu-lockd parses, not just
     * an auth-only marker word (see HandleSetMacroCommand). Throws
     * [org.pcbu.libreclient.crypto.CryptoException.BlobTooLarge] if the serialized macro
     * doesn't fit the shared AES-GCM blob cap — callers already surface send failures to the
     * user (see DeviceDetailScreen's status pattern), so no separate size check is needed here. */
    fun sendMacro(context: Context, deviceId: String, encKey: String, macroJson: String) =
        sendCommand(context, deviceId, encKey, cmd = "setMacro", marker = macroJson)

    /** Asks pcbu-lockd to play [macroJson] once right now, without storing it (the editor's
     * "Tester" button). Same payload format as [sendMacro]. */
    fun sendRunMacro(context: Context, deviceId: String, encKey: String, macroJson: String) =
        sendCommand(context, deviceId, encKey, cmd = "runMacro", marker = macroJson)

    /** Asks pcbu-lockd to connect back to this phone on [requestJson]'s port with the PC's
     * application list (see [PcAppListClient]). */
    fun sendListApps(context: Context, deviceId: String, encKey: String, requestJson: String) =
        sendCommand(context, deviceId, encKey, cmd = "listApps", marker = requestJson)

    /** Asks pcbu-lockd to pre-select this device's paired OS user at the login greeter
     * (GDM/SDDM/etc., via a synthetic keypress) so its PAM conversation starts without a
     * physical click — see pcbu-lockd's HandleSelectUserCommand. The existing unlock flow
     * (UDP/FCM wake -> biometric prompt) then proceeds exactly as it does today once that
     * PAM conversation is running. */
    fun sendSelectUser(context: Context, deviceId: String, encKey: String) =
        sendCommand(context, deviceId, encKey, cmd = "selectUser", marker = SELECT_USER_MARKER)

    /** Asks pcbu-lockd to suspend the PC (system sleep, not hibernate — see
     * PlatformHelper::Suspend). The app can't wake it back up: nothing on the PC can act
     * on an incoming command once it's actually asleep. */
    fun sendSuspend(context: Context, deviceId: String, encKey: String) =
        sendCommand(context, deviceId, encKey, cmd = "suspend", marker = SUSPEND_MARKER)

    /** Proximity lock heartbeat: pcbu-lockd locks this device's user session once it stops
     * hearing these for [timeoutSeconds] (see its CheckPresenceTimeouts). Unlike the other
     * markers here, the timeout IS the payload pcbu-lockd parses. */
    fun sendPresence(context: Context, deviceId: String, encKey: String, timeoutSeconds: Int) =
        sendCommand(context, deviceId, encKey, cmd = "presence", marker = timeoutSeconds.toString())

    /** Disarms the proximity lock right away (option turned off, or listening stopped by the
     * user), instead of letting pcbu-lockd lock the PC once heartbeats stop arriving. */
    fun sendPresenceOff(context: Context, deviceId: String, encKey: String) =
        sendCommand(context, deviceId, encKey, cmd = "presenceOff", marker = PRESENCE_OFF_MARKER)

    private fun sendCommand(context: Context, deviceId: String, encKey: String, cmd: String, marker: String) {
        val encrypted = AesGcmPacketCodec.encryptPacket(marker.toByteArray(Charsets.UTF_8), encKey)
        val request = LockRequest(deviceId = deviceId, encData = HexCodec.encode(encrypted), cmd = cmd)
        val payload = PcbuJson.encodeToString(LockRequest.serializer(), request).toByteArray(Charsets.UTF_8)

        val targets = broadcastAddresses(context)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (address in targets) {
                try {
                    socket.send(DatagramPacket(payload, payload.size, address, LOCK_LISTEN_PORT))
                } catch (e: Exception) {
                    // Best-effort per target; another candidate address (or the phone simply
                    // being off-network by then) may still get it through.
                }
            }
        }
    }

    private fun broadcastAddresses(context: Context): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val dhcpInfo = wifiManager?.dhcpInfo
            if (dhcpInfo != null && dhcpInfo.ipAddress != 0 && dhcpInfo.netmask != 0) {
                val broadcastInt = (dhcpInfo.ipAddress and dhcpInfo.netmask) or dhcpInfo.netmask.inv()
                val bytes = byteArrayOf(
                    (broadcastInt and 0xFF).toByte(),
                    (broadcastInt shr 8 and 0xFF).toByte(),
                    (broadcastInt shr 16 and 0xFF).toByte(),
                    (broadcastInt shr 24 and 0xFF).toByte(),
                )
                result.add(InetAddress.getByAddress(bytes))
            }
        } catch (e: Exception) {
            // Fall through to the limited-broadcast fallback below.
        }
        result.add(InetAddress.getByName("255.255.255.255"))
        return result
    }
}
