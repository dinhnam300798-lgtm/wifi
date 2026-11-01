package com.wifimaster.hardwaremonitor.data.repository

import com.wifimaster.hardwaremonitor.domain.model.DiscoveredDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArpSubnetScanner @Inject constructor() {

    /**
     * Scans local /24 subnet for active devices using concurrent socket handshakes
     * and parses the Linux /proc/net/arp table to extract hardware MAC addresses.
     */
    suspend fun scanSubnet(subnetPrefix: String = "192.168.1"): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val activeIps = mutableListOf<String>()

        // 1. Probe 254 subnet hosts concurrently with a lightweight 150ms timeout
        coroutineScope {
            val jobs = (1..254).map { host ->
                async {
                    val ip = "$subnetPrefix.$host"
                    if (isHostReachable(ip)) {
                        synchronized(activeIps) { activeIps.add(ip) }
                    }
                }
            }
            jobs.awaitAll()
        }

        // 2. Read ARP Table for MAC mapping
        val arpMap = readArpTable()

        // 3. Resolve vendor OUI and hostnames
        return@withContext activeIps.map { ip ->
            val mac = arpMap[ip] ?: "Unknown (Protected)"
            val vendor = resolveOuiVendor(mac)
            val hostname = try {
                InetAddress.getByName(ip).canonicalHostName
            } catch (e: Exception) { ip }

            DiscoveredDevice(
                ipAddress = ip,
                macAddress = mac,
                hostname = hostname,
                vendor = vendor,
                deviceType = classifyDevice(hostname, vendor),
                isOpenWebPort = checkPort(ip, 80) || checkPort(ip, 443)
            )
        }
    }

    private fun isHostReachable(host: String): Boolean {
        // Fast TCP handshake to common local ports (53, 80, 443, 8080)
        val commonPorts = intArrayOf(80, 443, 53, 8080, 554)
        for (port in commonPorts) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 120)
                    return true
                }
            } catch (e: Exception) {
                // Next port
            }
        }
        return false
    }

    private fun checkPort(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 100)
                true
            }
        } catch (e: Exception) { false }
    }

    private fun readArpTable(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line: String? = reader.readLine() // skip header
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line?.split("\\s+".toRegex()) ?: continue
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val mac = tokens[3]
                        if (mac != "00:00:00:00:00:00") {
                            map[ip] = mac
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Restricted on modern Android SELinux policies
        }
        return map
    }

    private fun resolveOuiVendor(mac: String): String {
        val cleanMac = mac.replace(":", "").uppercase()
        if (cleanMac.length < 6) return "Generic Device"
        val prefix = cleanMac.substring(0, 6)
        return when (prefix) {
            "001A2B", "3C5AB4", "BC926B" -> "Apple, Inc."
            "ACCC8E", "E0D55E", "5C56ED" -> "Samsung Electronics"
            "D858D7", "F4F5D8" -> "Google LLC (Pixel/Nest)"
            "001F3F", "A00460" -> "Sony Interactive (PlayStation)"
            "001132", "00224D" -> "Synology NAS"
            "B827EB", "DCA632" -> "Raspberry Pi Foundation"
            "001788" -> "Philips Hue Bridge"
            else -> "Network Hardware Device"
        }
    }

    private fun classifyDevice(hostname: String, vendor: String): String = when {
        vendor.contains("Apple") || vendor.contains("Samsung") || vendor.contains("Google") -> "Mobile / Tablet"
        vendor.contains("Sony") || hostname.contains("playstation") -> "Gaming Console"
        vendor.contains("Synology") -> "Network Attached Storage"
        vendor.contains("Philips") || hostname.contains("iot") -> "Smart IoT Appliance"
        else -> "Computer / Terminal"
    }
}