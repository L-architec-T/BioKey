package org.pcbu.libreclient.protocol.model

import kotlinx.serialization.json.Json

/**
 * Shared JSON config for all protocol payloads. ignoreUnknownKeys mirrors the
 * PC's own tolerant parsing (PacketPairInit::FromJson wraps optional fields
 * in try/catch), so a future protocol field on either side doesn't break
 * interop.
 */
val PcbuJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
