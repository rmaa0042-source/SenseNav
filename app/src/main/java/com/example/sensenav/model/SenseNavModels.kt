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

