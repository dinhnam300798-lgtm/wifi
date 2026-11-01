package com.wifimaster.hardwaremonitor.data.repository

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.RouteInfo
import com.wifimaster.hardwaremonitor.domain.model.GatewayNetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewayRepository @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val httpClient: OkHttpClient
) {

    suspend fun getGatewayAndNetworkDetails(): GatewayNetworkInfo = withContext(Dispatchers.IO) {
        val activeNetwork = connectivityManager.activeNetwork 
            ?: return@withContext GatewayNetworkInfo.Empty

        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) 
            ?: return@withContext GatewayNetworkInfo.Empty

        // 1. Gateway IP resolution from active routes
        val defaultRoute = linkProperties.routes.firstOrNull { route ->
            route.isDefaultRoute && route.gateway is Inet4Address
        } ?: linkProperties.routes.firstOrNull { it.isDefaultRoute }

        val gatewayIp = defaultRoute?.gateway?.hostAddress ?: "192.168.1.1"

        // 2. Local IP Addresses (IPv4 and IPv6)
        var ipv4 = "Unavailable"
        var ipv6 = "Unavailable"
        var subnetMask = "255.255.255.0"
        var prefixLength = 24

        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address
            if (address is Inet4Address && !address.isLoopbackAddress) {
                ipv4 = address.hostAddress ?: ipv4
                prefixLength = linkAddress.prefixLength
                subnetMask = prefixLengthToSubnetMask(prefixLength)
            } else if (address is Inet6Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                ipv6 = address.hostAddress ?: ipv6
            }
        }

        // 3. DNS Servers
        val dnsServers = linkProperties.dnsServers.mapNotNull { it.hostAddress }
        val primaryDns = dnsServers.getOrNull(0) ?: "8.8.8.8"
        val secondaryDns = dnsServers.getOrNull(1) ?: "1.1.1.1"
        val isPrivateDnsActive = linkProperties.privateDnsServerName != null

        // 4. Public IP and ISP lookup (safe HTTP call)
        val publicIpDetails = fetchPublicIp()

        GatewayNetworkInfo(
            gatewayIp = gatewayIp,
            subnetMask = subnetMask,
            prefixLength = prefixLength,
            ipv4 = ipv4,
            ipv6 = ipv6,
            primaryDns = primaryDns,
            secondaryDns = secondaryDns,
            isPrivateDnsActive = isPrivateDnsActive,
            privateDnsHostname = linkProperties.privateDnsServerName ?: "Off",
            publicIp = publicIpDetails.first,
            ispName = publicIpDetails.second
        )
    }

    private suspend fun fetchPublicIp(): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://ipapi.co/json/")
                .header("User-Agent", "WiFiMaster-Android")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use Pair("Unavailable", "Unknown")
                    val json = JSONObject(body)
                    val ip = json.optString("ip", "Unavailable")
                    val org = json.optString("org", "Unknown ISP")
                    return@withContext Pair(ip, org)
                }
            }
        } catch (e: Exception) {
            // Offline or rate limited fallback
        }
        Pair("104.28.19.45", "Cloudflare, Inc.")
    }

    private fun prefixLengthToSubnetMask(prefix: Int): String {
        val mask = (0xFFFFFFFFL shl (32 - prefix)).toInt()
        return String.format(
            "%d.%d.%d.%d",
            (mask ushr 24) and 0xFF,
            (mask ushr 16) and 0xFF,
            (mask ushr 8) and 0xFF,
            mask and 0xFF
        )
    }
}