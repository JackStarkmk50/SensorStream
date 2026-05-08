package com.example.sensorstream.data.sensor

import kotlin.math.*

/**
 * WGS84 geodetic arithmetic for GNSS coordinate transformations.
 * Ported from legacy GNSS_Arithmetic.java.
 */
object GnssArithmetic {

    private const val WGS84_A = 6378137.0           // Semi-major axis
    private const val WGS84_B = 6356752.3142        // Semi-minor axis
    private val WGS84_C = WGS84_A.pow(2) / WGS84_B
    private val WGS84_E2 = (WGS84_A.pow(2) - WGS84_B.pow(2)) / WGS84_A.pow(2)
    private val WGS84_EP2 = (WGS84_A.pow(2) - WGS84_B.pow(2)) / WGS84_B.pow(2)

    /**
     * Convert geodetic coordinates (lat/lon/alt in degrees/meters) to ECEF XYZ.
     */
    fun blhToXyz(latDeg: Double, lonDeg: Double, alt: Double): DoubleArray {
        val latRad = Math.toRadians(latDeg)
        val lonRad = Math.toRadians(lonDeg)

        val sinB = sin(latRad)
        val cosB = cos(latRad)
        val sinL = sin(lonRad)
        val cosL = cos(lonRad)

        val n = WGS84_C / sqrt(1.0 + WGS84_EP2 * cosB.pow(2))

        return doubleArrayOf(
            (n + alt) * cosB * cosL,
            (n + alt) * cosB * sinL,
            ((1.0 - WGS84_E2) * n + alt) * sinB
        )
    }

    /**
     * Convert speed + bearing in local NED frame to ECEF velocity vector.
     */
    fun velocityToEcef(
        speed: Double,
        bearingDeg: Double,
        latDeg: Double,
        lonDeg: Double
    ): DoubleArray {
        val bearRad = Math.toRadians(bearingDeg)
        val latRad = Math.toRadians(latDeg)
        val lonRad = Math.toRadians(lonDeg)

        val vNorth = cos(bearRad) * speed
        val vEast = sin(bearRad) * speed

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val sinLon = sin(lonRad)
        val cosLon = cos(lonRad)

        // Rotation matrix NED -> ECEF
        return doubleArrayOf(
            -sinLat * cosLon * vNorth - sinLon * vEast,
            -sinLat * sinLon * vNorth + cosLon * vEast,
            cosLat * vNorth
        )
    }
}

private fun Double.pow(n: Int): Double = Math.pow(this, n.toDouble())
