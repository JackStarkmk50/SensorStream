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

    companion object {
        private const val MAX_PACKET_SIZE = 1400 // Stay under MTU
        private const val FRAG_HEADER_SIZE = 12 // FRAG (4) + ID (4) + INDEX (2) + TOTAL (2)
    }

    suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        if (_connectionState.value != ConnectionState.Connected) return@withContext

        try {
            val s = socket ?: return@withContext
            val address = targetAddress ?: return@withContext
            
            if (data.size <= MAX_PACKET_SIZE) {
                val packet = DatagramPacket(data, data.size, address, port)
                s.send(packet)
            } else {
                // Fragment large data (usually camera frames)
                val frameId = (System.currentTimeMillis() % 1000000).toInt()
                val totalChunks = ((data.size + MAX_PACKET_SIZE - 1) / MAX_PACKET_SIZE)
                
                for (i in 0 until totalChunks) {
                    val start = i * MAX_PACKET_SIZE
                    val end = minOf(start + MAX_PACKET_SIZE, data.size)
                    val chunkSize = end - start
                    
                    val chunkData = ByteArray(FRAG_HEADER_SIZE + chunkSize)
                    // Write Header
                    System.arraycopy("FRAG".toByteArray(), 0, chunkData, 0, 4)
                    // Frame ID (4 bytes)
                    chunkData[4] = (frameId shr 24).toByte()
                    chunkData[5] = (frameId shr 16).toByte()
                    chunkData[6] = (frameId shr 8).toByte()
                    chunkData[7] = frameId.toByte()
                    // Chunk Index (2 bytes)
                    chunkData[8] = (i shr 8).toByte()
                    chunkData[9] = i.toByte()
                    // Total Chunks (2 bytes)
                    chunkData[10] = (totalChunks shr 8).toByte()
                    chunkData[11] = totalChunks.toByte()
                    
                    // Copy Payload
                    System.arraycopy(data, start, chunkData, FRAG_HEADER_SIZE, chunkSize)
                    
                    val packet = DatagramPacket(chunkData, chunkData.size, address, port)
                    s.send(packet)
                }
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Send failed: ${e.message}")
        }
    }

    fun disconnect() {
        socket?.close()
        socket = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
