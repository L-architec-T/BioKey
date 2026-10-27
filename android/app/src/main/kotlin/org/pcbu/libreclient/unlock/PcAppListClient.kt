package org.pcbu.libreclient.unlock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pcbu.libreclient.crypto.AesGcmPacketCodec
import org.pcbu.libreclient.crypto.HexCodec
import org.pcbu.libreclient.protocol.PacketFraming
import org.pcbu.libreclient.protocol.PacketIds
import org.pcbu.libreclient.protocol.model.AppListDto
import org.pcbu.libreclient.protocol.model.AppListHeader
import org.pcbu.libreclient.protocol.model.ListAppsRequest
import org.pcbu.libreclient.protocol.model.PcbuJson

/** An application installed on the PC, ready for the macro editor's picker. */
data class PcApp(val id: String, val name: String, val icon: Bitmap?)

sealed class PcAppListError(message: String) : Exception(message) {
    /** pcbu-lockd never connected back: PC off/asleep, other network, or a lockd older than
     * "listApps" (which treats any unknown cmd as "lock", so the PC got locked instead). */
    object NoAnswer : PcAppListError("Le PC n'a pas répondu")
    class Malformed(detail: String) : PcAppListError("Réponse du PC invalide : $detail")
}

/**
 * Downloads the PC's application list, however heavy (names + PNG icons). A UDP datagram
 * can't carry it, and the phone doesn't know the PC's IP, so the phone listens on a TCP port,
 * broadcasts a small "listApps" command carrying that port and a nonce, and pcbu-lockd
 * connects back to the datagram's source with the list encrypted once under the device key
 * and split in chunks (see SendAppList in pcbu-lockd's main.cpp).
 */
object PcAppListClient {
    private const val ACCEPT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000
    // Far above any real list (a few hundred apps with 96 KB icons max); only guards memory.
    private const val MAX_LIST_BYTES = 32 * 1024 * 1024

    suspend fun fetch(context: Context, deviceId: String, encKey: String): List<PcApp> = withContext(Dispatchers.IO) {
        ServerSocket(0).use { server ->
            server.soTimeout = ACCEPT_TIMEOUT_MS
            val nonce = HexCodec.encode(ByteArray(16).also(SecureRandom()::nextBytes))
            val request = PcbuJson.encodeToString(ListAppsRequest.serializer(), ListAppsRequest(server.localPort, nonce))
            LockBroadcastSender.sendListApps(context, deviceId, encKey, request)

            val socket = try {
                server.accept()
            } catch (e: SocketTimeoutException) {
                throw PcAppListError.NoAnswer
            }
            val encrypted = socket.use {
                it.soTimeout = READ_TIMEOUT_MS
                readChunks(it.getInputStream())
            }
            val plaintext = AesGcmPacketCodec.decryptPacket(encrypted, encKey, maxBlobSize = MAX_LIST_BYTES)
            val list = PcbuJson.decodeFromString(AppListDto.serializer(), plaintext.toString(Charsets.UTF_8))
            if (list.nonce != nonce) throw PcAppListError.Malformed("réponse à une autre demande")

            val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }
            list.apps
                .map { PcApp(it.id, it.name, it.icon?.let(::decodeIcon)) }
                .sortedWith { a, b -> collator.compare(a.name, b.name) }
        }
    }

    private fun readChunks(input: java.io.InputStream): ByteArray {
        val headerPacket = PacketFraming.readPacket(input)
        if (headerPacket.id != PacketIds.APP_LIST_HEADER) throw PcAppListError.Malformed("en-tête attendu")
        val header = PcbuJson.decodeFromString(AppListHeader.serializer(), headerPacket.data.toString(Charsets.UTF_8))
        if (header.size <= 0 || header.size > MAX_LIST_BYTES || header.chunks <= 0) {
            throw PcAppListError.Malformed("taille ${header.size}")
        }
        val out = ByteArrayOutputStream(header.size)
        repeat(header.chunks) {
            val chunk = PacketFraming.readPacket(input)
            if (chunk.id != PacketIds.APP_LIST_CHUNK) throw PcAppListError.Malformed("morceau attendu")
            if (out.size() + chunk.data.size > header.size) throw PcAppListError.Malformed("trop de données")
            out.write(chunk.data)
        }
        if (out.size() != header.size) throw PcAppListError.Malformed("liste incomplète")
        return out.toByteArray()
    }

    private fun decodeIcon(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
