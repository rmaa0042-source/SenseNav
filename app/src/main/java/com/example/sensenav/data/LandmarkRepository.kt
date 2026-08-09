package com.example.sensenav.data

import com.example.sensenav.api.LandmarkDto
import com.example.sensenav.api.SenseNavApi
import com.example.sensenav.api.SenseNavApiClient
import com.example.sensenav.model.GeoPoint
import com.example.sensenav.model.Refuge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sensory refuges sourced from the landmark dataset on the SenseNav API.
 *
 * The API tags every landmark Calm / Neutral / Busy, so asking it for the Calm
 * ones is what turns a general landmark list into a refuge list. That rating is
 * a rule-based estimate from the landmark's category, not a live sensor
 * reading - [sensoryTagFor] keeps the wording modest for that reason.
 */
class LandmarkRepository(
    private val api: SenseNavApi = SenseNavApiClient.api
) {

    /**
     * Calm-rated landmarks around [near], closest first. Returns an empty list
     * when the point falls outside the dataset's coverage, which callers should
     * treat as "nothing to recommend here" rather than a failure.
     */
    suspend fun getLowSensoryRefuges(
        near: GeoPoint,
        radiusKm: Double = DEFAULT_RADIUS_KM,
        limit: Int = DEFAULT_LIMIT
    ): List<Refuge> =
        withContext(Dispatchers.IO) {
            api.getNearbyLandmarks(
                lat = near.latitude,
                lng = near.longitude,
                radiusKm = radiusKm,
                sensoryRating = CALM,
                limit = limit
            ).landmarks.mapNotNull { it.toRefuge() }
        }

    /** Drops entries with no id, name or position - they cannot be mapped or routed to. */
    private fun LandmarkDto.toRefuge(): Refuge? {
        val id = landmarkId ?: return null
        val name = featureName?.takeIf { it.isNotBlank() } ?: return null
        val lat = latitude ?: return null
        val lng = longitude ?: return null

        val broad = theme?.takeIf { it.isNotBlank() }
        val specific = subTheme?.takeIf { it.isNotBlank() }

        return Refuge(
            id = "landmark_$id",
            name = name,
            subtitle = broad ?: specific ?: "Landmark",
            category = specific ?: broad ?: "Landmark",
            latitude = lat,
            longitude = lng,
            // The dataset has no user ratings, so this stays null and the star
            // line is left off rather than filled with an invented number.
            rating = null,
            sensoryTag = sensoryTagFor(sensoryRating),
            imageLabel = broad ?: specific ?: "Landmark",
            distanceKm = distanceKm,
            isSaved = false
        )
    }

    private fun sensoryTagFor(rating: String?): String = when (rating?.lowercase()) {
        "calm" -> "Low Sensory (estimated)"
        "neutral" -> "Moderate Sensory (estimated)"
        "busy" -> "High Sensory (estimated)"
        else -> "Sensory level unknown"
    }

    private companion object {
        const val CALM = "Calm"

        /**
         * The dataset only covers the City of Melbourne, so this is deliberately
         * wide enough to reach the CBD from anywhere in the metro area - at 10km
         * a user in the south-eastern suburbs gets an empty list, since the
         * nearest calm landmark to them is about 15km away. Results come back
         * closest-first, so a CBD user is unaffected by the wider cutoff.
         */
        const val DEFAULT_RADIUS_KM = 30.0

        /** Enough to fill the home carousel and the list beneath it. */
        const val DEFAULT_LIMIT = 20
    }
}
