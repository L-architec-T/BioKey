package org.pcbu.libreclient.crypto

/**
 * The PC side uses boost::algorithm::hex (uppercase output) / unhex
 * (case-insensitive input). Encoding here is arbitrary case; decoding
 * must tolerate either case to interoperate.
 */
object HexCodec {
    private val UPPER_HEX = "0123456789ABCDEF".toCharArray()

    fun encode(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = UPPER_HEX[v ushr 4]
            out[i * 2 + 1] = UPPER_HEX[v and 0x0F]
        }
        return String(out)
    }

    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have an even length: ${hex.length}" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex character in \"$hex\" at index ${i * 2}" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
