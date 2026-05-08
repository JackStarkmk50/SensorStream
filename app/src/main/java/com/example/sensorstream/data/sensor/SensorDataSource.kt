package com.example.sensorstream.data.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.sensorstream.data.model.SensorReading
import com.example.sensorstream.data.model.SensorType
import com.example.sensorstream.data.model.Vec3
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Flow-based sensor data source. Converts Android SensorEventListener
 * callbacks into Kotlin Flows for reactive consumption.
 */
class SensorDataSource(private val sensorManager: SensorManager) {

    private val typeToAndroid = mapOf(
        SensorType.ACCELEROMETER to Sensor.TYPE_ACCELEROMETER,
        SensorType.GYROSCOPE to Sensor.TYPE_GYROSCOPE,
        SensorType.MAGNETOMETER to Sensor.TYPE_MAGNETIC_FIELD,
        SensorType.ORIENTATION to Sensor.TYPE_ROTATION_VECTOR,
        SensorType.LINEAR_ACCELERATION to Sensor.TYPE_LINEAR_ACCELERATION,
        SensorType.GRAVITY to Sensor.TYPE_GRAVITY,
        SensorType.ROTATION_VECTOR to Sensor.TYPE_ROTATION_VECTOR,
        SensorType.PRESSURE to Sensor.TYPE_PRESSURE
    )

    private val androidToType = mapOf(
        Sensor.TYPE_ACCELEROMETER to SensorType.ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE to SensorType.GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD to SensorType.MAGNETOMETER,
        Sensor.TYPE_LINEAR_ACCELERATION to SensorType.LINEAR_ACCELERATION,
        Sensor.TYPE_GRAVITY to SensorType.GRAVITY,
        Sensor.TYPE_ROTATION_VECTOR to SensorType.ROTATION_VECTOR,
        Sensor.TYPE_PRESSURE to SensorType.PRESSURE
    )

    /**
     * Observe a single sensor type as a Flow of SensorReading.
     */
    fun observeSensor(sensorType: SensorType, delayUs: Int): Flow<SensorReading> = callbackFlow {
        val androidType = typeToAndroid[sensorType] ?: run {
            close()
            return@callbackFlow
        }
        val sensor = sensorManager.getDefaultSensor(androidType) ?: run {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val reading = SensorReading(
                    type = sensorType,
                    values = Vec3(
                        x = event.values.getOrElse(0) { 0f },
                        y = event.values.getOrElse(1) { 0f },
                        z = event.values.getOrElse(2) { 0f }
                    ),
                    timestampNs = event.timestamp,
                    accuracy = event.accuracy
                )
                trySend(reading)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, delayUs)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Returns the set of sensor types available on this device.
     */
    fun getAvailableSensors(): Set<SensorType> {
        return typeToAndroid.filter { (_, androidType) ->
            sensorManager.getDefaultSensor(androidType) != null
        }.keys
    }
}
