package com.wifimaster.hardwaremonitor.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.wifimaster.hardwaremonitor.domain.model.WifiHardwareInfo
import com.wifimaster.hardwaremonitor.domain.model.WifiStandard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.NetworkInterface
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiHardwareScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiManager: WifiManager,
    private val connectivityManager: ConnectivityManager
) {

    /**
     * Emits real-time Wi-Fi hardware metrics whenever network capabilities change.
     * Uses ConnectivityManager.NetworkCallback to obtain TransportInfo (WifiInfo)
     * as recommended in Android 12+ (API 31+) rather than deprecated wifiManager.connectionInfo.
     */
    @SuppressLint("MissingPermission")
    fun observeWifiHardware(): Flow<WifiHardwareInfo> = callbackFlow {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                // In Android 12+ (API 31+), WifiInfo is obtained safely via NetworkCapabilities.transportInfo
                val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    networkCapabilities.transportInfo as? WifiInfo
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager.connectionInfo
                }

                if (wifiInfo != null) {
                    val hardwareInfo = parseWifiInfo(wifiInfo, networkCapabilities)
                    trySend(hardwareInfo)
                }
            }

            override fun onLost(network: Network) {
                trySend(WifiHardwareInfo.Disconnected)
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, callback)

        // Send initial state immediately
        val currentInfo = getCurrentWifiHardwareInfo()
        trySend(currentInfo)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentWifiHardwareInfo(): WifiHardwareInfo {
        val activeNetwork = connectivityManager.activeNetwork ?: return WifiHardwareInfo.Disconnected
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) 
            ?: return WifiHardwareInfo.Disconnected

        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return WifiHardwareInfo.Disconnected
        }

        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo ?: @Suppress("DEPRECATION") wifiManager.connectionInfo
        } else {
            @Suppress("DEPRECATION") wifiManager.connectionInfo
        }

        return parseWifiInfo(wifiInfo, capabilities)
    }

    private fun parseWifiInfo(wifiInfo: WifiInfo, capabilities: NetworkCapabilities): WifiHardwareInfo {
        val ssid = wifiInfo.ssid.replace(""", "")
        val bssid = wifiInfo.bssid ?: "00:00:00:00:00:00"
        val rssi = wifiInfo.rssi
        val frequency = wifiInfo.frequency

        // 1. Detect Wi-Fi Standard (802.11n, 802.11ac, 802.11ax, 802.11be Wi-Fi 7)
        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (wifiInfo.wifiStandard) {
                WifiInfo.WIFI_STANDARD_11BE -> WifiStandard.WIFI_7 // Android 14+ (API 34)
                WifiInfo.WIFI_STANDARD_11AX -> {
                    // Check if 6 GHz band (5945 MHz - 7125 MHz)
                    if (frequency >= 5925) WifiStandard.WIFI_6E else WifiStandard.WIFI_6
                }
                WifiInfo.WIFI_STANDARD_11AC -> WifiStandard.WIFI_5
                WifiInfo.WIFI_STANDARD_11N -> WifiStandard.WIFI_4
                WifiInfo.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
                else -> WifiStandard.UNKNOWN
            }
        } else {
            estimateStandardFromFrequency(frequency)
        }

        // 2. Link Speeds (TX and RX PHY rates in Mbps)
        val txLinkSpeedMbps = wifiInfo.linkSpeed
        val rxLinkSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiInfo.rxLinkSpeedMbps
        } else {
            txLinkSpeedMbps
        }

        // Maximum supported link speeds (API 30+)
        val maxTxSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiInfo.maxSupportedTxLinkSpeedMbps
        } else txLinkSpeedMbps

        val maxRxSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiInfo.maxSupportedRxLinkSpeedMbps
        } else rxLinkSpeedMbps

        // 3. Channel & Bandwidth calculation
        val channel = frequencyToChannel(frequency)
        val channelBand = when {
            frequency in 2400..2484 -> "2.4 GHz"
            frequency in 5150..5895 -> "5 GHz"
            frequency in 5925..7125 -> "6 GHz"
            else -> "Unknown"
        }

        // 4. Signal Quality estimation (0 - 100%)
        // Standard RSSI range is typically -100 dBm (dead) to -30 dBm (maximum)
        val linkQualityPercent = WifiManager.calculateSignalLevel(rssi, 100)

        // 5. Device MAC Address and Randomization Status
        val (macAddress, isRandomized) = resolveMacStatus(wifiInfo)

        return WifiHardwareInfo(
            ssid = if (ssid == "<unknown ssid>") "Connected Wi-Fi (Permission Required)" else ssid,
            bssid = bssid,
            wifiStandard = standard,
            frequencyMhz = frequency,
            channel = channel,
            channelBand = channelBand,
            rssiDbm = rssi,
            linkQualityPercent = linkQualityPercent,
            txLinkSpeedMbps = txLinkSpeedMbps,
            rxLinkSpeedMbps = rxLinkSpeedMbps,
            maxTxSpeedMbps = maxTxSpeedMbps,
            maxRxSpeedMbps = maxRxSpeedMbps,
            macAddress = macAddress,
            isMacRandomized = isRandomized,
            isConnected = true
        )
    }

    /**
     * Resolves MAC address and determines if Android Randomized MAC is active.
     * Android 10+ randomizes MAC by default per SSID for anti-tracking privacy.
     */
    private fun resolveMacStatus(wifiInfo: WifiInfo): Pair<String, Boolean> {
        val reportedMac = wifiInfo.macAddress
        if (reportedMac != null && reportedMac != "02:00:00:00:00:00") {
            // Check 2nd least significant bit of first octet (Locally Administered bit)
            val firstByte = reportedMac.split(":").firstOrNull()?.toIntOrNull(16) ?: 0
            val isLocallyAdministered = (firstByte and 0x02) != 0
            return Pair(reportedMac, isLocallyAdministered)
        }

        // Fallback: Check network interfaces for active wlan0
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.name.equals("wlan0", ignoreCase = true)) {
                    val macBytes = intf.hardwareAddress ?: continue
                    val sb = StringBuilder()
                    for (b in macBytes) {
                        sb.append(String.format("%02X:", b))
                    }
                    if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
                    val mac = sb.toString()
                    val firstByte = macBytes[0].toInt()
                    val isRandomized = (firstByte and 0x02) != 0
                    return Pair(mac, isRandomized)
                }
            }
        } catch (e: Exception) {
            // Hardware inspection restricted by SELinux on API 30+
        }

        return Pair("Randomized per Network", true)
    }

    private fun frequencyToChannel(freqMhz: Int): Int = when {
        freqMhz == 2484 -> 14
        freqMhz in 2412..2472 -> (freqMhz - 2412) / 5 + 1
        freqMhz in 5170..5825 -> (freqMhz - 5170) / 5 + 34
        freqMhz in 5925..7125 -> (freqMhz - 5925) / 5 + 1 // 6GHz Wi-Fi 6E/7 channels
        else -> -1
    }

    private fun estimateStandardFromFrequency(freqMhz: Int): WifiStandard = when {
        freqMhz >= 5925 -> WifiStandard.WIFI_6E
        freqMhz >= 5000 -> WifiStandard.WIFI_5
        else -> WifiStandard.WIFI_4
    }
}