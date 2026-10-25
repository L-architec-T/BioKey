package org.pcbu.libreclient.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SimpleVector(val sharedKey: String, val plaintextHex: String, val blobHex: String)

@Serializable
data class PacketVector(val sharedKey: String, val plaintextHex: String, val timestampMs: Long, val blobHex: String)

@Serializable
data class GoldenVectors(val simple: List<SimpleVector>, val packet: List<PacketVector>)

object GoldenVectorsLoader {
    fun load(): GoldenVectors {
        val text = requireNotNull(
            GoldenVectorsLoader::class.java.classLoader.getResourceAsStream("golden_vectors.json")
        ) { "golden_vectors.json missing from test resources; run tools/interop_harness/generate_golden_vectors.py" }
            .bufferedReader()
            .readText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(GoldenVectors.serializer(), text)
    }
}
