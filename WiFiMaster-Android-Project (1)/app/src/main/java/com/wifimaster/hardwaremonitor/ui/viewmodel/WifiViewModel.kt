package com.wifimaster.hardwaremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifimaster.hardwaremonitor.data.repository.ArpSubnetScanner
import com.wifimaster.hardwaremonitor.data.repository.GatewayRepository
import com.wifimaster.hardwaremonitor.data.repository.SpeedTestEngine
import com.wifimaster.hardwaremonitor.data.repository.WifiHardwareScanner
import com.wifimaster.hardwaremonitor.domain.model.DiscoveredDevice
import com.wifimaster.hardwaremonitor.domain.model.GatewayNetworkInfo
import com.wifimaster.hardwaremonitor.domain.model.SpeedTestMetric
import com.wifimaster.hardwaremonitor.domain.model.WifiHardwareInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WifiDashboardUiState(
    val hardwareInfo: WifiHardwareInfo = WifiHardwareInfo.Disconnected,
    val gatewayInfo: GatewayNetworkInfo = GatewayNetworkInfo.Empty,
    val speedTestState: SpeedTestUiState = SpeedTestUiState(),
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val isScanningSubnet: Boolean = false,
    val hasFineLocationPermission: Boolean = false,
    val hasNearbyDevicesPermission: Boolean = false,
    val signalHistoryDbm: List<Int> = emptyList()
)

data class SpeedTestUiState(
    val isRunning: Boolean = false,
    val stage: String = "Idle",
    val pingMs: Float = 0f,
    val jitterMs: Float = 0f,
    val downloadMbps: Float = 0f,
    val uploadMbps: Float = 0f,
    val progress: Float = 0f
)

@HiltViewModel
class WifiViewModel @Inject constructor(
    private val hardwareScanner: WifiHardwareScanner,
    private val gatewayRepository: GatewayRepository,
    private val speedTestEngine: SpeedTestEngine,
    private val subnetScanner: ArpSubnetScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiDashboardUiState())
    val uiState: StateFlow<WifiDashboardUiState> = _uiState.asStateFlow()

    init {
        observeWifiHardware()
        loadGatewayDetails()
    }

    fun onPermissionsResult(locationGranted: Boolean, nearbyGranted: Boolean) {
        _uiState.update { it.copy(
            hasFineLocationPermission = locationGranted,
            hasNearbyDevicesPermission = nearbyGranted
        ) }
        // Re-read Wi-Fi info immediately with newly granted permission
        if (locationGranted) {
            val freshInfo = hardwareScanner.getCurrentWifiHardwareInfo()
            _uiState.update { it.copy(hardwareInfo = freshInfo) }
        }
    }

    private fun observeWifiHardware() {
        viewModelScope.launch {
            hardwareScanner.observeWifiHardware().collect { info ->
                _uiState.update { state ->
                    val updatedHistory = (state.signalHistoryDbm + info.rssiDbm).takeLast(60)
                    state.copy(
                        hardwareInfo = info,
                        signalHistoryDbm = updatedHistory
                    )
                }
            }
        }
    }

    fun loadGatewayDetails() {
        viewModelScope.launch {
            val gateway = gatewayRepository.getGatewayAndNetworkDetails()
            _uiState.update { it.copy(gatewayInfo = gateway) }
        }
    }

    fun startSpeedTest() {
        if (_uiState.value.speedTestState.isRunning) return

        viewModelScope.launch {
            _uiState.update { it.copy(speedTestState = SpeedTestUiState(isRunning = true, stage = "Initializing...")) }
            speedTestEngine.runSpeedBenchmark().collect { metric ->
                when (metric) {
                    is SpeedTestMetric.Starting -> {
                        _uiState.update { it.copy(speedTestState = it.speedTestState.copy(stage = metric.message)) }
                    }
                    is SpeedTestMetric.PingComplete -> {
                        _uiState.update { it.copy(speedTestState = it.speedTestState.copy(
                            pingMs = metric.pingMs,
                            jitterMs = metric.jitterMs
                        )) }
                    }
                    is SpeedTestMetric.Downloading -> {
                        _uiState.update { it.copy(speedTestState = it.speedTestState.copy(
                            stage = "Downloading",
                            downloadMbps = metric.mbps,
                            progress = metric.progress
                        )) }
                    }
                    is SpeedTestMetric.DownloadComplete -> {
                        _uiState.update { it.copy(speedTestState = it.speedTestState.copy(
                            downloadMbps = metric.finalMbps
                        )) }
                    }
                    is SpeedTestMetric.Uploading -> {
                        _uiState.update { it.copy(speedTestState = it.speedTestState.copy(
                            stage = "Uploading",
                            uploadMbps = metric.mbps,
                            progress = metric.progress
                        )) }
                    }
                    is SpeedTestMetric.Finished -> {
                        _uiState.update { it.copy(speedTestState = it.speedTestState.copy(
                            isRunning = false,
                            stage = "Completed",
                            pingMs = metric.ping,
                            jitterMs = metric.jitter,
                            downloadMbps = metric.downloadMbps,
                            uploadMbps = metric.uploadMbps,
                            progress = 1f
                        )) }
                    }
                }
            }
        }
    }

    fun scanSubnetDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningSubnet = true) }
            val devices = subnetScanner.scanSubnet()
            _uiState.update { it.copy(discoveredDevices = devices, isScanningSubnet = false) }
        }
    }
}