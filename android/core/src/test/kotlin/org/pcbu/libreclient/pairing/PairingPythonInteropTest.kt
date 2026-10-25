package org.pcbu.libreclient.pairing

import java.io.BufferedReader
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cross-language interop check: pairs the real Kotlin PairingTcpClient
 * against tools/interop_harness/pcbu_fake_pc_server.py — an independent
 * Python implementation of the PC side. This is the strongest signal the
 * project has that the framing + AES-GCM packet crypto are wire-compatible,
 * short of testing against the real pcbu-desktop binary.
 *
 * Skips (does not fail) if python3 or the harness script aren't available,
 * so it never blocks CI environments without Python.
 */
class PairingPythonInteropTest {

    private fun findRepoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: throw IllegalStateException("Could not locate repo root from ${System.getProperty("user.dir")}")
        }
        return dir
    }

    private fun python3Available(): Boolean = try {
        ProcessBuilder("python3", "--version").start().waitFor() == 0
    } catch (e: Exception) {
        false
    }

    @Test
    fun `pairs against the independent Python fake PC server and matches the sent deviceUUID`() {
        assumeTrue(python3Available(), "python3 not available; skipping cross-language interop test")

        val harnessDir = File(findRepoRoot(), "tools/interop_harness")
        assumeTrue(File(harnessDir, "pcbu_fake_pc_server.py").exists(), "interop harness script not found")

        val encKey = "interop-test-key-9000"
        val responseData = """{"pairingMethod":"UDP","deviceId":"py-device-id","deviceName":"debian",
            |"deviceOS":"Linux","unlockServerPort":43296,"macAddresses":["aa:bb:cc:dd:ee:ff"],
            |"userName":"larchitect","passwordKey":"pk-from-python-9000"}""".trimMargin().replace("\n", "")

        val process = ProcessBuilder(
            "python3", "pcbu_fake_pc_server.py", "--enc-key", encKey, "--accept", responseData,
        ).directory(harnessDir).redirectErrorStream(false).start()

        val stdout: BufferedReader = process.inputStream.bufferedReader()
        val portLine = stdout.readLine() ?: throw IllegalStateException("Python server produced no output")
        assertTrue(portLine.startsWith("PORT "), "Unexpected first line from server: $portLine")
        val port = portLine.removePrefix("PORT ").trim().toInt()

        val thisDevice = ThisDeviceInfo(
            deviceUUID = "kotlin-test-uuid-42",
            deviceName = "Kotlin Test Client",
            ipAddress = "10.0.0.5",
            udpPort = 43301,
        )

        val record = try {
            PairingTcpClient.pair("127.0.0.1", port, encKey, thisDevice)
        } finally {
            process.waitFor()
        }

        assertEquals("py-device-id", record.deviceId)
        assertEquals("pk-from-python-9000", record.passwordKey)
        assertEquals(listOf("aa:bb:cc:dd:ee:ff"), record.macAddresses)

        val receivedInitLine = generateSequence(stdout::readLine).firstOrNull { it.startsWith("RECEIVED_INIT ") }
            ?: throw IllegalStateException("Python server never logged RECEIVED_INIT")
        val receivedInitJson = kotlinx.serialization.json.Json.parseToJsonElement(
            receivedInitLine.removePrefix("RECEIVED_INIT ").trim(),
        ).jsonObject
        assertEquals(
            "kotlin-test-uuid-42",
            receivedInitJson["deviceUUID"]?.jsonPrimitive?.content,
            "Python server did not see the expected deviceUUID: $receivedInitLine",
        )
        assertEquals(0, process.exitValue(), "Python fake server exited non-zero")
    }
}
