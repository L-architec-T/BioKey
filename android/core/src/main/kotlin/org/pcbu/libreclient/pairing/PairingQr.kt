package org.pcbu.libreclient.pairing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.pcbu.libreclient.protocol.model.LegacyPairingQrData
import org.pcbu.libreclient.protocol.model.PairingQrData

/**
 * A scanned PCBU pairing QR is either the "modern" format (serverId + a UDP
 * discovery port, resolved to an IP via an encrypted beacon) or the "legacy"
 * format (IP baked directly into the QR). Discriminated by the presence of
 * "serverId" vs "ip" in the JSON, per pcbu-desktop's PairingForm — the
 * modern format is the current default (legacy requires an explicit
 * checkbox on the PC), so callers should attempt modern discovery unless
 * the user is known to have ticked that box.
 */
sealed interface PairingTarget {
    val method: String
    val encKey: String

    data class Legacy(val ip: String, val port: Int, override val method: String, override val encKey: String) : PairingTarget

    data class Modern(val serverId: String, val discoveryPort: Int, override val method: String, override val encKey: String) : PairingTarget
}

class MalformedPairingQrException(raw: String, cause: Throwable? = null) :
    Exception("Not a recognizable PCBU pairing QR payload: $raw", cause)

object PairingQrParser {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    /**
     * [raw] is either the QR's JSON or the pairing code typed from the PC screen: that same
     * JSON in base32 (pcbu-desktop's StringUtils::ToBase32String, no padding), shown in groups
     * of 5 separated by "-". Case, spaces and dashes are ignored.
     */
    fun parse(raw: String): PairingTarget {
        val json = raw.trim().let { if (it.startsWith("{")) it else decodePairingCode(it) ?: it }
        val obj = try {
            lenientJson.parseToJsonElement(json).jsonObject
        } catch (e: Exception) {
            throw MalformedPairingQrException(raw, e)
        }

        return try {
            when {
                "serverId" in obj -> {
                    val data = lenientJson.decodeFromJsonElement(PairingQrData.serializer(), obj)
                    PairingTarget.Modern(data.serverId, data.port, data.method, data.encKey)
                }
                "ip" in obj -> {
                    val data = lenientJson.decodeFromJsonElement(LegacyPairingQrData.serializer(), obj)
                    PairingTarget.Legacy(data.ip, data.port, data.method, data.encKey)
                }
                else -> throw MalformedPairingQrException(raw)
            }
        } catch (e: MalformedPairingQrException) {
            throw e
        } catch (e: Exception) {
            throw MalformedPairingQrException(raw, e)
        }
    }

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Base32 pairing code → JSON text, or null if it isn't valid base32. */
    internal fun decodePairingCode(code: String): String? {
        val chars = code.uppercase().filter { it.isLetterOrDigit() }
        if (chars.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in chars) {
            val v = BASE32_ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
