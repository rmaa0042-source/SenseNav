package com.example.sensenav.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.sensenav.model.GeoPoint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/**
 * Reads the device's real position for use as the route origin.
 *
 * Returns null rather than throwing when location is unavailable (permission
 * denied, location services off, no fix yet) so callers can fall back to a
 * sensible default instead of failing the whole screen.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // guarded by hasPermission() above
    suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()

        return try {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellation.token
            ).await()?.let { GeoPoint(it.latitude, it.longitude) }
                ?: client.lastLocation.await()?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
