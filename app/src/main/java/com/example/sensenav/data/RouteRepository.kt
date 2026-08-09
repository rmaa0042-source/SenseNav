package com.example.sensenav.data

import com.example.sensenav.api.LatLngDto
import com.example.sensenav.api.RouteRequestDto
import com.example.sensenav.api.ScoredRouteDto
import com.example.sensenav.api.SenseNavApi
import com.example.sensenav.api.SenseNavApiClient
import com.example.sensenav.model.GeoPoint
import com.example.sensenav.model.RouteResult
import com.example.sensenav.model.ScoredRoute
import com.example.sensenav.model.Sensitivity
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Fetches sensory-scored walking routes from the SenseNav routing API and
 * decodes each route's encoded polyline into drawable coordinates.
 *
 * Results are cached for [CACHE_TTL_MS]. A route query costs a Google Directions
 * call plus a sweep of the sensor dataset, and retrying the same trip - which
 * the screen does on every rotation, back-navigation and re-entry - was paying
 * that twice for an answer that had not changed.
 *
 * The cache is deliberately in memory only. Its keys are the user's origins and
 * destinations, and writing those to disk would leave a durable record of where
 * they have been, which is the one thing the rest of this app takes care not to
 * do. It also bounds staleness at process lifetime, which matters because the
 * sensitivity scores are live pedestrian readings.
 */
class RouteRepository(
    private val api: SenseNavApi = SenseNavApiClient.api,
    private val now: () -> Long = System::currentTimeMillis
) {

    private data class CacheEntry(val result: RouteResult, val fetchedAt: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()

    suspend fun getRoutes(origin: GeoPoint, destination: GeoPoint): RouteResult =
        withContext(Dispatchers.IO) {
            val key = cacheKey(origin, destination)

            mutex.withLock {
                cache[key]?.takeIf { now() - it.fetchedAt < CACHE_TTL_MS }?.result
            }?.let { return@withContext it }

            val response = api.getScoredRoutes(
                RouteRequestDto(
                    origin = LatLngDto(origin.latitude, origin.longitude),
                    destination = LatLngDto(destination.latitude, destination.longitude)
                )
            )

            val routes = response.routes.mapNotNull { it.toDomain() }
            if (routes.isEmpty()) {
                // Not cached: an empty answer is usually a transient upstream
                // problem, and holding it for 15 minutes would strand the user.
                throw IllegalStateException("The routing service returned no walkable routes.")
            }

            val result = RouteResult(
                isScored = response.mode.equals("scored", ignoreCase = true),
                routes = routes
            )

            mutex.withLock {
                cache.entries.removeAll { now() - it.value.fetchedAt >= CACHE_TTL_MS }
                cache[key] = CacheEntry(result, now())
            }

            result
        }

    /**
     * Coordinates are rounded to about 11 metres before keying. A device fix
     * jitters by a few metres while standing still, and without rounding every
     * such wobble would look like a new trip and miss the cache.
     */
    private fun cacheKey(origin: GeoPoint, destination: GeoPoint): String =
        "%.4f,%.4f>%.4f,%.4f".format(
            origin.latitude,
            origin.longitude,
            destination.latitude,
            destination.longitude
        )

    /** Drops routes with no usable geometry - they cannot be drawn on the map. */
    private fun ScoredRouteDto.toDomain(): ScoredRoute? {
        val decoded = polyline
            ?.takeIf { it.isNotBlank() }
            ?.let { PolyUtil.decode(it) }
            ?.map { GeoPoint(it.latitude, it.longitude) }
            .orEmpty()

        if (decoded.size < 2) return null

        return ScoredRoute(
            summary = summary?.takeIf { it.isNotBlank() } ?: "Walking route",
            path = decoded,
            distanceText = distanceText.orEmpty(),
            durationText = durationText.orEmpty(),
            sensitivity = Sensitivity.from(sensitivity),
            colorHex = color,
            avgPedestrianCount = avgPedestrianCount
        )
    }

    private companion object {
        /**
         * Short by design. These routes carry live pedestrian-sensor ratings, and
         * a rating is a claim about how busy somewhere is right now.
         */
        const val CACHE_TTL_MS = 15 * 60 * 1000L
    }
}
