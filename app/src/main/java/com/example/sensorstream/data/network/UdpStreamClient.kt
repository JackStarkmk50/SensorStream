package com.example.sensorstream.data.network

import com.example.sensorstream.data.model.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * UDP Streaming client for high-frequency, low-latency transmission.
 */
class UdpStreamClient {

    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var port: Int = 0

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    suspend fun connect(ip: String, targetPort: Int) = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.Connecting
            targetAddress = InetAddress.getByName(ip)
            port = targetPort
            socket = DatagramSocket()
            _connectionState.value = ConnectionState.Connected
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Failed to resolve host")
        }
    }

    suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        if (_connectionState.value != ConnectionState.Connected) return@withContext

        try {
            val s = socket ?: return@withContext
            val address = targetAddress ?: return@withContext
            val packet = DatagramPacket(data, data.size, address, port)
            s.send(packet)
        } catch (e: Exception) {
            // UDP is connectionless, but socket might be closed
            _connectionState.value = ConnectionState.Error("Socket closed")
        }
    }

    fun disconnect() {
        socket?.close()
        socket = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
