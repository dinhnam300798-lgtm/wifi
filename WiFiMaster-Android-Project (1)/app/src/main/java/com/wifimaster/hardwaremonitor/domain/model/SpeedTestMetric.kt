package com.wifimaster.hardwaremonitor.domain.model

sealed class SpeedTestMetric {
    data class Starting(val message: String) : SpeedTestMetric()
    data class PingComplete(val pingMs: Float, val jitterMs: Float) : SpeedTestMetric()
    data class Downloading(val mbps: Float, val progress: Float) : SpeedTestMetric()
    data class DownloadComplete(val finalMbps: Float) : SpeedTestMetric()
    data class Uploading(val mbps: Float, val progress: Float) : SpeedTestMetric()
    data class Finished(
        val ping: Float,
        val jitter: Float,
        val downloadMbps: Float,
        val uploadMbps: Float
    ) : SpeedTestMetric()
}
