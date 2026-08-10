package com.example.sensenav.model

/**
 * A single point on a route path. The scoring API returns each route as a
 * Google-encoded polyline string, which decodes into a list of these.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * A place the app can recommend as a sensory refuge.
 *
 * [rating] and [distanceKm] are null when the source has no such value - the
 * landmark dataset carries neither a user rating nor, for a locally held
 * refuge, a distance - so the UI omits them rather than showing a made-up one.
 */
data class Refuge(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double?,
    val sensoryTag: String,
    val imageLabel: String,
    val distanceKm: Double? = null,
    val isSaved: Boolean = false
)

/**
 * A route the user kept for later.
 *
 * Stores the text the card showed rather than the polyline: geometry and
 * pedestrian counts go stale within the hour, and a saved route is a reminder
 * of a choice, not a cached result to replay.
 */
data class SavedRoute(
    val id: String,
    val summary: String,
    val destinationName: String,
    val durationText: String,
    val distanceText: String,
    val sensitivityLabel: String
)

/**
 * A location the user pinned by hand instead of using the device's own.
 *
 * [label] is what they searched for rather than what the geocoder called it, so
 * the home screen echoes their own words back to them.
 */
data class SavedPlace(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * A freely-licensed photograph of a refuge, sourced from Wikimedia.
 *
 * [artist] and [license] are carried alongside the URL because the CC licences
 * these images use require credit wherever the photo is shown - a bare URL is
 * not enough to display one lawfully.
 */
data class LandmarkImage(
    val url: String,
    val artist: String?,
    val license: String?,
    val descriptionUrl: String?
) {
    /** Short credit line, e.g. "Medelam / CC BY-SA 4.0". */
    val credit: String
        get() = listOfNotNull(artist, license).joinToString(" / ").ifBlank { "Wikimedia Commons" }
}

/** Sensory load of a route, as classified by the scoring API. */
enum class Sensitivity(val rank: Int) {
    Low(0),
    Medium(1),
    High(2),
    Unknown(3);

    companion object {
        fun from(raw: String?): Sensitivity = when (raw?.lowercase()) {
            "low" -> Low
            "medium" -> Medium
            "high" -> High
            else -> Unknown
        }
    }
}

/** One walking route returned by the scoring API, with its polyline decoded. */
data class ScoredRoute(
    val summary: String,
    val path: List<GeoPoint>,
    val distanceText: String,
    val durationText: String,
    val sensitivity: Sensitivity,
    val colorHex: String?,
    val avgPedestrianCount: Double?
) {
    /** True when this route carries a real sensor-backed sensory rating. */
    val isScored: Boolean get() = sensitivity != Sensitivity.Unknown
}

/**
 * Result of a route query. When [isScored] is false the API found no sensor
 * coverage for the trip, so the single route returned must be shown as a plain
 * route with no sensory claim.
 */
data class RouteResult(
    val isScored: Boolean,
    val routes: List<ScoredRoute>
)

/**
 * The sensory bands the user set for themselves, and how far out to look for
 * landmarks.
 *
 * The two bounds are pedestrian counts, the one sensory quantity the API
 * actually measures, and they default to the same cut-offs the scoring service
 * applies server-side. An untouched filter therefore bands every route exactly
 * as the backend already did - only a deliberate change moves one.
 *
 * Two bounds rather than three: three independent values could be set to
 * contradict each other (a "high" below the "low"), and the third band is
 * simply everything above [mediumMaxPedestrians].
 */
data class SensoryFilter(
    val lowMaxPedestrians: Int = DEFAULT_LOW_MAX,
    val mediumMaxPedestrians: Int = DEFAULT_MEDIUM_MAX,
    val radiusKm: Int = DEFAULT_RADIUS_KM
) {

    /**
     * The band [avgPedestrianCount] falls into. A route with no sensor reading
     * stays [Sensitivity.Unknown]: there is nothing to compare it against, and
     * a threshold cannot manufacture a rating the data never carried.
     */
    fun classify(avgPedestrianCount: Double?): Sensitivity = when {
        avgPedestrianCount == null -> Sensitivity.Unknown
        avgPedestrianCount < lowMaxPedestrians -> Sensitivity.Low
        avgPedestrianCount < mediumMaxPedestrians -> Sensitivity.Medium
        else -> Sensitivity.High
    }

    /**
     * Keeps the bands ordered and in range however they were arrived at - a
     * slider drag, or a value written by an older build.
     */
    fun normalised(): SensoryFilter {
        val low = lowMaxPedestrians.coerceIn(PEDESTRIAN_STEP, MAX_LOW_PEDESTRIANS)
        return SensoryFilter(
            lowMaxPedestrians = low,
            mediumMaxPedestrians = mediumMaxPedestrians
                .coerceIn(low + PEDESTRIAN_STEP, MAX_PEDESTRIANS),
            radiusKm = radiusKm.coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)
        )
    }

    companion object {
        // Mirrors score_route() in the API: under 30 Low, under 100 Medium,
        // otherwise High. Changing these changes what an unconfigured user sees.
        const val DEFAULT_LOW_MAX = 30
        const val DEFAULT_MEDIUM_MAX = 100

        const val DEFAULT_RADIUS_KM = 10
        const val MIN_RADIUS_KM = 1
        const val MAX_RADIUS_KM = 30

        /** Well past the busiest sensor readings in the dataset. */
        const val MAX_PEDESTRIANS = 500
        const val PEDESTRIAN_STEP = 5

        /**
         * The Low bound stops halfway up. Past that it stops meaning "quiet",
         * and it keeps a workable span left for the Medium band above it - a
         * Low pinned just under the ceiling would leave Medium a single step
         * wide, which is not a range anyone can aim at on a slider.
         */
        const val MAX_LOW_PEDESTRIANS = MAX_PEDESTRIANS / 2
    }
}

/**
 * Re-bands the scored routes against the user's own thresholds.
 *
 * The API's colour is dropped from any route this rebands: that colour encodes
 * the server's band, so keeping it would paint a route labelled Low in the
 * colour of a High one. Routes that came back with no sensor reading pass
 * through untouched, since there is no count to re-band them on.
 */
fun RouteResult.withThresholds(filter: SensoryFilter): RouteResult = copy(
    routes = routes.map { route ->
        if (route.avgPedestrianCount == null) {
            route
        } else {
            route.copy(
                sensitivity = filter.classify(route.avgPedestrianCount),
                colorHex = null
            )
        }
    }
)

data class SearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: SearchResultType,
    val sensoryLabel: String
)

enum class SearchResultType {
    Station,
    Refuge,
    Route,
    Warning
}

