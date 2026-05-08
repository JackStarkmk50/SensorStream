package com.example.sensorstream.data.model

/**
 * 3D vector for sensor readings.
 */
data class Vec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

/**
 * Sensor type enumeration matching legacy CSV IDs for backward compatibility.
 */
enum class SensorType(val csvId: Int, val displayName: String, val unit: String) {
    ACCELEROMETER(3, "Accelerometer", "m/s²"),
    GYROSCOPE(4, "Gyroscope", "rad/s"),
    MAGNETOMETER(5, "Magnetometer", "μT"),
    ORIENTATION(81, "Orientation", "°"),
    LINEAR_ACCELERATION(82, "Linear Accel", "m/s²"),
    GRAVITY(83, "Gravity", "m/s²"),
    ROTATION_VECTOR(84, "Rotation Vector", ""),
    PRESSURE(85, "Pressure", "hPa")
}

/**
 * A single sensor reading with timestamp.
 */
data class SensorReading(
    val type: SensorType,
    val values: Vec3,
    val timestampNs: Long,
    val accuracy: Int = 0
)

/**
 * GPS/GNSS reading with WGS84 coordinates.
 */
data class GpsReading(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float = 0f,
    val timestampMs: Long = 0L,
    val xyzWgs84: DoubleArray = DoubleArray(3),
    val velocityEcef: DoubleArray = DoubleArray(3)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GpsReading) return false
        return latitude == other.latitude && longitude == other.longitude && timestampMs == other.timestampMs
    }
    override fun hashCode(): Int = timestampMs.hashCode()
}

/**
 * Complete snapshot of all sensor data at a point in time.
 */
data class SensorSnapshot(
    val sensors: Map<SensorType, SensorReading> = emptyMap(),
    val gps: GpsReading? = null,
    val batteryTemp: Int = 0,
    val timestampNs: Long = System.nanoTime()
)
