package org.pcbu.libreclient.net

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Picks a local IPv4 address to report in PairInit.ipAddress. Deliberately
 * avoids WifiManager.getConnectionInfo() (requires location permission on
 * some Android versions) in favor of plain NetworkInterface enumeration,
 * which needs no permissions and works identically on the JVM for tests.
 */
object LocalNetworkInfo {

    fun findLocalIPv4Address(): String? {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList()
        } catch (e: Exception) {
            null
        } ?: return null

        return interfaces
            .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()
            ?.hostAddress
    }
}
