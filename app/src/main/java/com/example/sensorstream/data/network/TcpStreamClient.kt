package com.example.sensorstream.data.network

import com.example.sensorstream.data.model.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket

/**
 * TCP Streaming client for reliable, connection-oriented data transmission.
 */
class TcpStreamClient {

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    suspend fun connect(ip: String, targetPort: Int) = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.Connecting
            val address = InetAddress.getByName(ip)
            socket = Socket(address, targetPort)
            outputStream = DataOutputStream(socket?.getOutputStream())
            _connectionState.value = ConnectionState.Connected
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Failed to connect via TCP")
            disconnect()
        }
    }

    suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        if (_connectionState.value != ConnectionState.Connected) return@withContext

        try {
            outputStream?.write(data)
            outputStream?.flush()
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("TCP Socket closed or broken pipe")
            disconnect()
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            // Ignore close errors
        } finally {
            outputStream = null
            socket = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }
}
