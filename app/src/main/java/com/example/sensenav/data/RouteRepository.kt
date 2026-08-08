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
import kotlinx.coroutines.withContext

/**
 * Fetches sensory-scored walking routes from the SenseNav routing API and
 * decodes each route's encoded polyline into drawable coordinates.
 */
class RouteRepository(
    private val api: SenseNavApi = SenseNavApiClient.api
) {

    suspend fun getRoutes(origin: GeoPoint, destination: GeoPoint): RouteResult =
        withContext(Dispatchers.IO) {
            val response = api.getScoredRoutes(
                RouteRequestDto(
                    origin = LatLngDto(origin.latitude, origin.longitude),
                    destination = LatLngDto(destination.latitude, destination.longitude)
                )
            )

            val routes = response.routes.mapNotNull { it.toDomain() }
            if (routes.isEmpty()) {
                throw IllegalStateException("The routing service returned no walkable routes.")
            }

            RouteResult(
                isScored = response.mode.equals("scored", ignoreCase = true),
                routes = routes
            )
        }

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
}
