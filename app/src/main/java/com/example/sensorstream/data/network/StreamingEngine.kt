package com.example.sensorstream.data.network

import android.util.Log
import com.example.sensorstream.data.model.ConnectionState
import com.example.sensorstream.data.model.SensorSnapshot
import com.example.sensorstream.data.model.StreamConfig
import com.example.sensorstream.data.model.StreamMode
import com.example.sensorstream.data.model.StreamProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Unified manager for starting, stopping, and handling the streaming lifecycle.
 */
class StreamingEngine {

    private val udpClient = UdpStreamClient()
    // TCP and WebSocket can be added here following the same pattern

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var streamingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startStreaming(config: StreamConfig) {
        if (_connectionState.value == ConnectionState.Connected) return

        streamingJob = scope.launch {
            if (config.streamMode == StreamMode.NETWORK_ONLY || config.streamMode == StreamMode.NETWORK_AND_FILE) {
                when (config.protocol) {
                    StreamProtocol.UDP -> {
                        udpClient.connect(config.targetIp, config.targetPort)
                        udpClient.connectionState.collect { state ->
                            _connectionState.value = state
                        }
                    }
                    StreamProtocol.TCP -> {
                        // Implement TCP
                        _connectionState.value = ConnectionState.Error("TCP not implemented yet")
                    }
                    StreamProtocol.WEBSOCKET -> {
                        // Implement WS
                        _connectionState.value = ConnectionState.Error("WS not implemented yet")
                    }
                }
            } else {
                // File only mode
                _connectionState.value = ConnectionState.Connected
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        udpClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun streamSnapshot(snapshot: SensorSnapshot, config: StreamConfig) {
        if (_connectionState.value != ConnectionState.Connected) return

        val csvString = PacketSerializer.toCSV(snapshot)
        val payload = PacketSerializer.toBytes(csvString + "\r\n")

        if (config.streamMode == StreamMode.NETWORK_ONLY || config.streamMode == StreamMode.NETWORK_AND_FILE) {
            when (config.protocol) {
                StreamProtocol.UDP -> udpClient.send(payload)
                StreamProtocol.TCP -> {} // TODO
                StreamProtocol.WEBSOCKET -> {} // TODO
            }
        }

        if (config.streamMode == StreamMode.FILE_ONLY || config.streamMode == StreamMode.NETWORK_AND_FILE) {
            // File logging logic is handled by the FileLogger (injected elsewhere)
            // Just structural placeholder here
        }
    }
}
