package com.example.sensenav.data

import com.example.sensenav.model.GeoPoint
import com.example.sensenav.model.Refuge
import com.example.sensenav.model.RouteOption
import com.example.sensenav.model.SearchResult
import com.example.sensenav.model.SearchResultType
import com.example.sensenav.model.WarningInfo

class MockSenseNavRepository {
    // Flinders Street Station -> State Library Victoria, the pair the route and
    // warning screens demo. Replaced by decoded API polylines once /route is wired up.
    private val flindersStreetStation = GeoPoint(-37.8183, 144.9671)
    private val stateLibrary = GeoPoint(-37.8098, 144.9652)

    private val refuges = listOf(
        Refuge(
            id = "refuge_state_library",
            name = "State Library Victoria",
            subtitle = "Swanston St, Melbourne CBD",
            category = "Quiet Zone",
            latitude = -37.8098,
            longitude = 144.9652,
            rating = 4.5,
            sensoryTag = "Low Sensory Zone",
            imageLabel = "Library",
            isSaved = true
        ),
        Refuge(
            id = "refuge_carlton_gardens",
            name = "Carlton Gardens",
            subtitle = "Victoria St, Carlton",
            category = "Green Space",
            latitude = -37.8063,
            longitude = 144.9717,
            rating = 4.5,
            sensoryTag = "Green Space",
            imageLabel = "Garden",
            isSaved = true
        ),
        Refuge(
            id = "refuge_fitzroy_gardens",
            name = "Fitzroy Gardens Quiet Path",
            subtitle = "Wellington Parade, East Melbourne",
            category = "Quiet Path",
            latitude = -37.8136,
            longitude = 144.9806,
            rating = 4.0,
            sensoryTag = "Low Stimulation",
            imageLabel = "Path"
        ),
        Refuge(
            id = "refuge_flagstaff_gardens",
            name = "Flagstaff Gardens Quiet Area",
            subtitle = "King St, West Melbourne",
            category = "Quiet Sanctuary",
            latitude = -37.8100,
            longitude = 144.9549,
            rating = 4.7,
            sensoryTag = "Quiet Sanctuary",
            imageLabel = "Park"
        )
    )

    private val routes = listOf(
        RouteOption(
            id = "route_direct",
            title = "Direct Route",
            sensoryRisk = "High Sensory Risk",
            rating = 4.0,
            reviewCount = 156,
            durationMinutes = 5,
            roadName = "Highway Road",
            isRecommended = false,
            sensoryScore = 42,
            // Straight up Swanston St - shortest, but past Bourke St Mall.
            path = listOf(
                flindersStreetStation,
                GeoPoint(-37.8175, 144.9666),
                GeoPoint(-37.8152, 144.9657),
                GeoPoint(-37.8130, 144.9650),
                GeoPoint(-37.8110, 144.9648),
                stateLibrary
            )
        ),
        RouteOption(
            id = "route_low_sensory",
            title = "Low Sensory Route",
            sensoryRisk = "Recommended",
            rating = 4.5,
            reviewCount = 240,
            durationMinutes = 5,
            roadName = "Downtown Road",
            isRecommended = true,
            sensoryScore = 86,
            // Swings east via the quieter Russell St side streets.
            path = listOf(
                flindersStreetStation,
                GeoPoint(-37.8176, 144.9690),
                GeoPoint(-37.8150, 144.9700),
                GeoPoint(-37.8125, 144.9685),
                GeoPoint(-37.8105, 144.9668),
                stateLibrary
            )
        )
    )

    private val warnings = listOf(
        WarningInfo(
            id = "warning_bourke_st_mall",
            title = "Sensory Overload Warning",
            locationName = "Bourke St Mall",
            densityPercent = 85,
            riskSummary = "High Congestion + High Noise Risks",
            dataSource = "City of Melbourne Pedestrian Sensors",
            suggestedAction = "Reroute to Low Sensory Path",
            routeId = "route_direct"
        )
    )

    private val recentSearches = listOf(
        SearchResult(
            id = "search_flinders",
            title = "Flinders Street Station",
            subtitle = "Flinders St, Melbourne CBD",
            type = SearchResultType.Station,
            sensoryLabel = "Busy interchange"
        ),
        SearchResult(
            id = "search_state_library",
            title = "State Library Victoria",
            subtitle = "Swanston St, Melbourne CBD",
            type = SearchResultType.Refuge,
            sensoryLabel = "Quiet Zone"
        ),
        SearchResult(
            id = "search_carlton",
            title = "Carlton Gardens",
            subtitle = "Victoria St, Carlton",
            type = SearchResultType.Refuge,
            sensoryLabel = "Green Space"
        )
    )

    fun getRefuges(): List<Refuge> = refuges

    fun getRefugeDetail(id: String): Refuge? = refuges.firstOrNull { it.id == id }

    fun getRoutes(): List<RouteOption> = routes

    fun getRouteDetail(id: String): RouteOption? = routes.firstOrNull { it.id == id }

    fun getWarnings(): List<WarningInfo> = warnings

    fun getWarningDetail(id: String): WarningInfo? = warnings.firstOrNull { it.id == id }

    fun getRecentSearches(): List<SearchResult> = recentSearches

    fun search(keyword: String): List<SearchResult> {
        val normalized = keyword.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        val refugeResults = refuges.map {
            SearchResult(
                id = it.id,
                title = it.name,
                subtitle = it.subtitle,
                type = SearchResultType.Refuge,
                sensoryLabel = it.sensoryTag
            )
        }

        val routeResults = routes.map {
            SearchResult(
                id = it.id,
                title = it.title,
                subtitle = "${it.durationMinutes} min via ${it.roadName}",
                type = SearchResultType.Route,
                sensoryLabel = it.sensoryRisk
            )
        }

        return (recentSearches + refugeResults + routeResults)
            .distinctBy { it.id }
            .filter {
                it.title.lowercase().contains(normalized) ||
                    it.subtitle.lowercase().contains(normalized) ||
                    it.sensoryLabel.lowercase().contains(normalized)
            }
    }
}
