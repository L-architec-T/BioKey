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

    fun parse(raw: String): PairingTarget {
        val obj = try {
            lenientJson.parseToJsonElement(raw).jsonObject
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
}
