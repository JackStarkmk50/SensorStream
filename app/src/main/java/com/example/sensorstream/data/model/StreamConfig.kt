package com.example.sensorstream.data.model

/**
 * Network protocol for streaming.
 */
enum class StreamProtocol(val displayName: String) {
    UDP("UDP"),
    TCP("TCP"),
    WEBSOCKET("WebSocket")
}

/**
 * Streaming output mode.
 */
enum class StreamMode(val displayName: String) {
    NETWORK_ONLY("Network Only"),
    FILE_ONLY("File Only"),
    NETWORK_AND_FILE("Network + File")
}

/**
 * Sensor sampling rate configuration.
 */
enum class SensorRate(val delayUs: Int, val displayName: String, val approxHz: String) {
    FASTEST(0, "Fastest", "~200Hz"),
    GAME(20_000, "Game", "~50Hz"),
    UI(60_000, "UI", "~16Hz"),
    NORMAL(200_000, "Normal", "~5Hz")
}

/**
 * Complete streaming configuration.
 */
data class StreamConfig(
    val targetIp: String = "192.168.1.100",
    val targetPort: Int = 5555,
    val protocol: StreamProtocol = StreamProtocol.UDP,
    val streamMode: StreamMode = StreamMode.NETWORK_ONLY,
    val sensorRate: SensorRate = SensorRate.GAME,
    val enabledSensors: Set<SensorType> = setOf(
        SensorType.ACCELEROMETER,
        SensorType.GYROSCOPE,
        SensorType.MAGNETOMETER
    ),
    val gpsEnabled: Boolean = false,
    val runInBackground: Boolean = false,
    val fileName: String = "sensorstream"
)

/**
 * Network connection state.
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
