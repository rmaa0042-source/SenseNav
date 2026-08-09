package com.example.sensenav.data

import android.content.Context
import android.location.Geocoder
import com.example.sensenav.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Converts between typed place names and coordinates using Android's built-in
 * [Geocoder].
 *
 * Uses the platform geocoder rather than Places Autocomplete deliberately: it
 * needs no API key and no billing, so it stays inside the free Maps SDK usage.
 */
class PlaceGeocoder(private val context: Context) {

    suspend fun resolve(query: String): GeoPoint? {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || !Geocoder.isPresent()) return null

        return withContext(Dispatchers.IO) {
            // Bias to greater Melbourne so "Flinders Street" resolves locally
            // rather than to a same-named street overseas.
            lookup(trimmed, boundToMelbourne = true)
                ?: lookup(trimmed, boundToMelbourne = false)
        }
    }

    /**
     * A short human-readable name for a point - "Carlton, Victoria" - for
     * telling the user where they are. Null when the device has no geocoder
     * available or nothing matches, so callers must have a fallback.
     */
    @Suppress("DEPRECATION") // async variant is API 33+; this project supports 26+
    suspend fun describe(point: GeoPoint): String? {
        if (!Geocoder.isPresent()) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                Geocoder(context)
                    .getFromLocation(point.latitude, point.longitude, 1)
                    ?.firstOrNull()
                    ?.let { address ->
                        // Suburb first where the device reports one, since it
                        // locates the user more precisely than the city does.
                        val area = address.subLocality
                            ?: address.locality
                            ?: address.subAdminArea
                        listOfNotNull(area, address.adminArea)
                            .joinToString(", ")
                            .takeIf { it.isNotBlank() }
                    }
            }.getOrNull()
        }
    }

    @Suppress("DEPRECATION") // async variant is API 33+; this project supports 26+
    private fun lookup(query: String, boundToMelbourne: Boolean): GeoPoint? = runCatching {
        val geocoder = Geocoder(context)
        val results = if (boundToMelbourne) {
            geocoder.getFromLocationName(
                query,
                1,
                MELBOURNE_MIN_LAT,
                MELBOURNE_MIN_LNG,
                MELBOURNE_MAX_LAT,
                MELBOURNE_MAX_LNG
            )
        } else {
            geocoder.getFromLocationName(query, 1)
        }

        results?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
    }.getOrNull()

    private companion object {
        const val MELBOURNE_MIN_LAT = -38.10
        const val MELBOURNE_MIN_LNG = 144.50
        const val MELBOURNE_MAX_LAT = -37.50
        const val MELBOURNE_MAX_LNG = 145.40
    }
}
