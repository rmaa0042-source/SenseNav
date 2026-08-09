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

data class WarningInfo(
    val id: String,
    val title: String,
    val locationName: String,
    val densityPercent: Int,
    val riskSummary: String,
    val dataSource: String,
    val suggestedAction: String,
    val routeId: String
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

