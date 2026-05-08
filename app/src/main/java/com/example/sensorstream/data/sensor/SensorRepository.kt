package com.example.sensorstream.data.sensor

import android.location.LocationManager
import com.example.sensorstream.data.model.SensorReading
import com.example.sensorstream.data.model.SensorSnapshot
import com.example.sensorstream.data.model.SensorType
import com.example.sensorstream.data.model.StreamConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Manages the aggregation of individual sensor flows into a single SensorSnapshot.
 */
class SensorRepository(
    private val sensorDataSource: SensorDataSource,
    private val locationDataSource: LocationDataSource
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var collectionJob: Job? = null

    private val _currentSnapshot = MutableStateFlow(SensorSnapshot())
    val currentSnapshot: StateFlow<SensorSnapshot> = _currentSnapshot.asStateFlow()

    fun startCollecting(config: StreamConfig) {
        collectionJob?.cancel()
        collectionJob = scope.launch {
            // A thread-safe map to track the latest readings from concurrent coroutines
            val latestSensors = java.util.concurrent.ConcurrentHashMap<SensorType, SensorReading>()

            // Launch sensor collectors
            config.enabledSensors.forEach { type ->
                launch {
                    sensorDataSource.observeSensor(type, config.sensorRate.delayUs).collect { reading ->
                        latestSensors[type] = reading
                        _currentSnapshot.value = _currentSnapshot.value.copy(
                            sensors = latestSensors.toMap(),
                            timestampNs = reading.timestampNs
                        )
                    }
                }
            }

            // Launch GPS collector if enabled
            if (config.gpsEnabled) {
                launch {
                    locationDataSource.observeLocation().collectLatest { gpsReading ->
                        _currentSnapshot.value = _currentSnapshot.value.copy(
                            gps = gpsReading
                        )
                    }
                }
            }
        }
    }

    fun stopCollecting() {
        collectionJob?.cancel()
        collectionJob = null
    }

    fun getAvailableSensors() = sensorDataSource.getAvailableSensors()
}
