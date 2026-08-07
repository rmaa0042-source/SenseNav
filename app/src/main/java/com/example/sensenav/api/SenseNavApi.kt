package com.example.sensenav.api

import com.example.sensenav.model.Refuge
import com.example.sensenav.model.RouteOption
import com.example.sensenav.model.SearchResult
import com.example.sensenav.model.WarningInfo
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

object SenseNavEndpoints {
    const val REFUGES = "/api/refuges"
    const val REFUGE_DETAIL = "/api/refuges/{id}"
    const val SEARCH = "/api/search"
    const val ROUTES = "/api/routes"
    const val ROUTE_DETAIL = "/api/routes/{id}"
    const val WARNINGS = "/api/warnings"
    const val WARNING_DETAIL = "/api/warnings/{id}"
}

interface SenseNavApi {
    // Homepage and Nearby Map: GET /api/refuges
    @GET(SenseNavEndpoints.REFUGES)
    suspend fun getRefuges(): List<Refuge>

    // Refuge Detail: GET /api/refuges/{id}
    @GET(SenseNavEndpoints.REFUGE_DETAIL)
    suspend fun getRefugeDetail(
        @Path("id") id: String
    ): Refuge

    // Search: GET /api/search?keyword=library
    @GET(SenseNavEndpoints.SEARCH)
    suspend fun search(
        @Query("keyword") keyword: String
    ): List<SearchResult>

    // Route Options: GET /api/routes
    @GET(SenseNavEndpoints.ROUTES)
    suspend fun getRoutes(): List<RouteOption>

    // Route Detail: GET /api/routes/{id}
    @GET(SenseNavEndpoints.ROUTE_DETAIL)
    suspend fun getRouteDetail(
        @Path("id") id: String
    ): RouteOption

    // Warning: GET /api/warnings
    @GET(SenseNavEndpoints.WARNINGS)
    suspend fun getWarnings(): List<WarningInfo>

    // Warning Detail: GET /api/warnings/{id}
    @GET(SenseNavEndpoints.WARNING_DETAIL)
    suspend fun getWarningDetail(
        @Path("id") id: String
    ): WarningInfo
}
