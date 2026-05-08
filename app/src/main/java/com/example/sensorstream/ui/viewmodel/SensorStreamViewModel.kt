package com.example.sensorstream.ui.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sensorstream.data.model.ConnectionState
import com.example.sensorstream.data.model.SensorSnapshot
import com.example.sensorstream.data.model.StreamConfig
import com.example.sensorstream.data.network.StreamingEngine
import com.example.sensorstream.data.sensor.LocationDataSource
import com.example.sensorstream.data.sensor.SensorDataSource
import com.example.sensorstream.data.sensor.SensorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SensorStreamViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val sensorDataSource = SensorDataSource(sensorManager)
    private val locationDataSource = LocationDataSource(locationManager)
    
    private val sensorRepository = SensorRepository(sensorDataSource, locationDataSource)
    private val streamingEngine = StreamingEngine()

    private val _streamConfig = MutableStateFlow(StreamConfig())
    val streamConfig: StateFlow<StreamConfig> = _streamConfig.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    val currentSnapshot: StateFlow<SensorSnapshot> = sensorRepository.currentSnapshot
    val connectionState: StateFlow<ConnectionState> = streamingEngine.connectionState

    init {
        // Forward snapshot data to streaming engine when streaming is active
        viewModelScope.launch {
            sensorRepository.currentSnapshot.collect { snapshot ->
                if (_isStreaming.value) {
                    streamingEngine.streamSnapshot(snapshot, _streamConfig.value)
                }
            }
        }
    }

    fun toggleStreaming() {
        if (_isStreaming.value) {
            stopStreaming()
        } else {
            startStreaming()
        }
    }

    private fun startStreaming() {
        val config = _streamConfig.value
        sensorRepository.startCollecting(config)
        streamingEngine.startStreaming(config)
        _isStreaming.value = true
    }

    private fun stopStreaming() {
        sensorRepository.stopCollecting()
        streamingEngine.stopStreaming()
        _isStreaming.value = false
    }

    fun updateConfig(config: StreamConfig) {
        _streamConfig.value = config
        // If already streaming, restart with new config
        if (_isStreaming.value) {
            stopStreaming()
            startStreaming()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
    }
}
