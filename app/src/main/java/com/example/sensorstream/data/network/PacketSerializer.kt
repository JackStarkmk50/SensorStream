package com.example.sensorstream.data.network

import com.example.sensorstream.data.model.*
import java.util.Locale

/**
 * Serializes sensor data into CSV strings compatible with the legacy
 * SensorStream packet format, and also supports a compact binary format.
 */
object PacketSerializer {

    private const val NS2S = 1.0 / 1_000_000_000.0

    /**
     * Serialize a SensorSnapshot into a CSV string (legacy-compatible format).
     * Format: timestamp, csvId, x, y, z, csvId, x, y, z, ...
     */
    fun toCSV(snapshot: SensorSnapshot): String {
        val sb = StringBuilder(256)
        val timestampSec = snapshot.timestampNs * NS2S
        sb.append(String.format(Locale.ENGLISH, "%.5f", timestampSec))

        // GPS data at front (legacy behavior)
        snapshot.gps?.let { gps ->
            sb.append(String.format(
                Locale.ENGLISH, ", 1, %10.6f,%11.6f,%6.1f",
                gps.latitude, gps.longitude, gps.altitude
            ))
        }

        // Sensor data
        for ((type, reading) in snapshot.sensors) {
            if (type == SensorType.PRESSURE) {
                sb.append(String.format(
                    Locale.ENGLISH, ", %d, %6.3f",
                    type.csvId, reading.values.x
                ))
            } else {
                sb.append(String.format(
                    Locale.ENGLISH, ", %d, %6.3f,%6.3f,%6.3f",
                    type.csvId, reading.values.x, reading.values.y, reading.values.z
                ))
            }
        }

        // GPS ECEF coordinates
        snapshot.gps?.let { gps ->
            sb.append(String.format(
                Locale.ENGLISH, ", 6, %12.3f,%12.3f,%12.3f",
                gps.xyzWgs84[0], gps.xyzWgs84[1], gps.xyzWgs84[2]
            ))
            sb.append(String.format(
                Locale.ENGLISH, ", 7, %6.3f,%6.3f,%6.3f",
                gps.velocityEcef[0], gps.velocityEcef[1], gps.velocityEcef[2]
            ))
            sb.append(String.format(Locale.ENGLISH, ", 8, %d", gps.timestampMs))
        }

        return sb.toString()
    }

    /**
     * Convert CSV string to UTF-8 bytes for network transmission.
     */
    fun toBytes(csvData: String): ByteArray {
        return csvData.toByteArray(Charsets.UTF_8)
    }
}
