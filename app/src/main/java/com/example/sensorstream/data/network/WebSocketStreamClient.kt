package com.example.sensorstream.data.network

import com.example.sensorstream.data.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

/**
 * WebSocket Streaming client leveraging OkHttp3.
 */
class WebSocketStreamClient {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    fun connect(ip: String, port: Int) {
        _connectionState.value = ConnectionState.Connecting
        
        // Handle cases where the user inputs a full ngrok url vs a raw IP
        val urlString = if (ip.startsWith("ws://") || ip.startsWith("wss://")) {
            // User provided a full URL, ignore the port parameter
            ip
        } else {
            // User provided a raw IP/domain, append the port
            "ws://$ip:$port/"
        }

        val request = Request.Builder()
            .url(urlString)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.Connected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.Error(t.message ?: "WebSocket connection failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.Disconnected
            }
        })
    }

    fun send(data: ByteArray) {
        if (_connectionState.value == ConnectionState.Connected) {
            // OkHttp handles the background threading internally for sending
            val success = webSocket?.send(data.toByteString()) == true
            if (!success) {
                _connectionState.value = ConnectionState.Error("Failed to send WebSocket frame")
            }
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
