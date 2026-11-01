package com.wifimaster.hardwaremonitor.domain.model

enum class WifiStandard(val displayName: String, val ieeeCode: String) {
    WIFI_7("Wi-Fi 7", "802.11be"),
    WIFI_6E("Wi-Fi 6E", "802.11ax 6GHz"),
    WIFI_6("Wi-Fi 6", "802.11ax"),
    WIFI_5("Wi-Fi 5", "802.11ac"),
    WIFI_4("Wi-Fi 4", "802.11n"),
    LEGACY("Legacy Wi-Fi", "802.11a/b/g"),
    UNKNOWN("Unknown", "N/A")
}

data class WifiHardwareInfo(
    val ssid: String = "",
    val bssid: String = "",
    val wifiStandard: WifiStandard = WifiStandard.UNKNOWN,
    val frequencyMhz: Int = 0,
    val channel: Int = 0,
    val channelBand: String = "",
    val rssiDbm: Int = -100,
    val linkQualityPercent: Int = 0,
    val txLinkSpeedMbps: Int = 0,
    val rxLinkSpeedMbps: Int = 0,
    val maxTxSpeedMbps: Int = 0,
    val maxRxSpeedMbps: Int = 0,
    val macAddress: String = "",
    val isMacRandomized: Boolean = true,
    val isConnected: Boolean = false
) {
    companion object {
        val Disconnected = WifiHardwareInfo()
    }
}
