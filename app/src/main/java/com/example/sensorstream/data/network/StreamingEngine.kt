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
    private val tcpClient = TcpStreamClient()
    private val wsClient = WebSocketStreamClient()

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
                        udpClient.connectionState.collect { _connectionState.value = it }
                    }
                    StreamProtocol.TCP -> {
                        tcpClient.connect(config.targetIp, config.targetPort)
                        tcpClient.connectionState.collect { _connectionState.value = it }
                    }
                    StreamProtocol.WEBSOCKET -> {
                        wsClient.connect(config.targetIp, config.targetPort)
                        wsClient.connectionState.collect { _connectionState.value = it }
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
        tcpClient.disconnect()
        wsClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun streamSnapshot(snapshot: SensorSnapshot, config: StreamConfig) {
        if (_connectionState.value != ConnectionState.Connected) return

        val csvString = PacketSerializer.toCSV(snapshot)
        val payload = PacketSerializer.toBytes(csvString + "\r\n")

        if (config.streamMode == StreamMode.NETWORK_ONLY || config.streamMode == StreamMode.NETWORK_AND_FILE) {
            when (config.protocol) {
                StreamProtocol.UDP -> udpClient.send(payload)
                StreamProtocol.TCP -> tcpClient.send(payload)
                StreamProtocol.WEBSOCKET -> wsClient.send(payload)
            }
        }

        if (config.streamMode == StreamMode.FILE_ONLY || config.streamMode == StreamMode.NETWORK_AND_FILE) {
            // File logging logic is handled by the FileLogger
        }
    }
}
