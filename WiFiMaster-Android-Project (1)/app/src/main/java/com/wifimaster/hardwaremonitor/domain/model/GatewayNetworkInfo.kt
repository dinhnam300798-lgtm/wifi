package com.wifimaster.hardwaremonitor.domain.model

data class GatewayNetworkInfo(
    val gatewayIp: String = "192.168.1.1",
    val subnetMask: String = "255.255.255.0",
    val prefixLength: Int = 24,
    val ipv4: String = "192.168.1.14",
    val ipv6: String = "fe80::1",
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "8.8.8.8",
    val isPrivateDnsActive: Boolean = true,
    val privateDnsHostname: String = "cloudflare-dns.com",
    val publicIp: String = "104.28.19.45",
    val ispName: String = "Cloudflare High-Speed"
) {
    companion object {
        val Empty = GatewayNetworkInfo()
    }
}
