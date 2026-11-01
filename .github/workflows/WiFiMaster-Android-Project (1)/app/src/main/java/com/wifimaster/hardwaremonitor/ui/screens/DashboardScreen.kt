package com.wifimaster.hardwaremonitor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wifimaster.hardwaremonitor.domain.model.WifiHardwareInfo
import com.wifimaster.hardwaremonitor.ui.viewmodel.WifiDashboardUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: WifiDashboardUiState,
    onStartSpeedTest: () -> Unit,
    onRefreshGateway: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WiFi Master & Monitor", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = uiState.hardwareInfo.ssid,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshGateway) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Alert Banner if Location Missing
            if (!uiState.hasFineLocationPermission) {
                item {
                    PermissionNoticeCard(onRequestPermissions)
                }
            }

            // Primary Wi-Fi Standard & Signal Card
            item {
                WifiStandardHeroCard(uiState.hardwareInfo)
            }

            // PHY Link Speeds (TX and RX rates)
            item {
                PhyLinkSpeedSection(uiState.hardwareInfo)
            }

            // Frequency, Channel & Security Specs
            item {
                ChannelAndFrequencyCard(uiState.hardwareInfo)
            }

            // Gateway & DNS Information
            item {
                GatewaySummaryCard(uiState.gatewayInfo)
            }
        }
    }
}

@Composable
fun WifiStandardHeroCard(info: WifiHardwareInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = info.wifiStandard.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${info.wifiStandard.ieeeCode} · ${info.channelBand}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // RSSI dBm Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = getRssiColor(info.rssiDbm).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${info.rssiDbm} dBm",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = getRssiColor(info.rssiDbm)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Linear Progress for Link Quality
            LinearProgressIndicator(
                progress = { info.linkQualityPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Link Quality", style = MaterialTheme.typography.labelSmall)
                Text("${info.linkQualityPercent}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun PhyLinkSpeedSection(info: WifiHardwareInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SpeedMetricCard(
            title = "TX PHY Rate",
            speedMbps = info.txLinkSpeedMbps,
            icon = Icons.Default.ArrowUpward,
            modifier = Modifier.weight(1f)
        )
        SpeedMetricCard(
            title = "RX PHY Rate",
            speedMbps = info.rxLinkSpeedMbps,
            icon = Icons.Default.ArrowDownward,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SpeedMetricCard(
    title: String,
    speedMbps: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "$speedMbps",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Mbps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getRssiColor(rssi: Int): Color = when {
    rssi >= -50 -> Color(0xFF10B981) // Excellent (Emerald)
    rssi >= -65 -> Color(0xFF3B82F6) // Good (Blue)
    rssi >= -75 -> Color(0xFFF59E0B) // Fair (Amber)
    else -> Color(0xFFEF4444)        // Poor (Red)
}