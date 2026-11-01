package com.wifimaster.hardwaremonitor.domain.model

data class DiscoveredDevice(
    val ipAddress: String,
    val macAddress: String,
    val hostname: String,
    val vendor: String,
    val deviceType: String,
    val isOpenWebPort: Boolean
)
