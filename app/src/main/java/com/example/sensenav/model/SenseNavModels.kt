package com.example.sensenav.model

/**
 * A single point on a route path. The scoring API returns each route as a
 * Google-encoded polyline string, which decodes into a list of these.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class Refuge(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val sensoryTag: String,
    val imageLabel: String,
    val isSaved: Boolean = false
)

data class RouteOption(
    val id: String,
    val title: String,
    val sensoryRisk: String,
    val rating: Double,
    val reviewCount: Int,
    val durationMinutes: Int,
    val roadName: String,
    val isRecommended: Boolean,
    val sensoryScore: Int,
    val path: List<GeoPoint> = emptyList()
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

