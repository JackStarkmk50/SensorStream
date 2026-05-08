package com.example.sensorstream.data.sensor

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.example.sensorstream.data.model.GpsReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Flow-based GPS/GNSS location data source.
 */
class LocationDataSource(private val locationManager: LocationManager) {

    val isGpsEnabled: Boolean
        get() = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    /**
     * Observe GPS location updates as a Flow.
     * Requires ACCESS_FINE_LOCATION permission.
     */
    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<GpsReading> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val xyz = GnssArithmetic.blhToXyz(
                    location.latitude, location.longitude, location.altitude
                )
                val velocity = GnssArithmetic.velocityToEcef(
                    location.speed.toDouble(),
                    location.bearing.toDouble(),
                    location.latitude,
                    location.longitude
                )
                val reading = GpsReading(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    speed = location.speed,
                    bearing = location.bearing,
                    accuracy = location.accuracy,
                    timestampMs = location.time,
                    xyzWgs84 = xyz,
                    velocityEcef = velocity
                )
                trySend(reading)
            }

            @Deprecated("Deprecated in API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0L,      // minimum time interval
            0f,      // minimum distance
            listener,
            Looper.getMainLooper()
        )

        awaitClose {
            locationManager.removeUpdates(listener)
        }
    }
}
