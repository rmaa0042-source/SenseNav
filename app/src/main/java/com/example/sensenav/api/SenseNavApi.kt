package com.example.sensenav.api

import com.example.sensenav.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class LatLngDto(
    val lat: Double,
    val lng: Double
)

data class RouteRequestDto(
    val origin: LatLngDto,
    val destination: LatLngDto
)

/**
 * `mode` is either "scored" (at least one route has live sensor data) or
 * "unscored" (no sensor coverage for this trip - a single route comes back with
 * null sensitivity and colour, and must not be presented as sensory-rated).
 */
data class ScoredRoutesResponseDto(
    val mode: String? = null,
    val routes: List<ScoredRouteDto> = emptyList()
)

data class ScoredRouteDto(
    val summary: String? = null,
    val polyline: String? = null,
    @SerializedName("distance_text") val distanceText: String? = null,
    @SerializedName("duration_text") val durationText: String? = null,
    val sensitivity: String? = null,
    val color: String? = null,
    @SerializedName("avg_pedestrian_count") val avgPedestrianCount: Double? = null
)

data class HealthDto(
    val status: String? = null
)

interface SenseNavApi {

    /** Health check - confirms the VM is up and the address is still valid. */
    @GET("/")
    suspend fun health(): HealthDto

    /**
     * Returns 2-3 walking routes between two points, each scored against City of
     * Melbourne pedestrian sensor data and returned as an encoded polyline.
     */
    @POST("/route")
    suspend fun getScoredRoutes(@Body request: RouteRequestDto): ScoredRoutesResponseDto
}

object SenseNavApiClient {

    val api: SenseNavApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Route scoring queries Google Directions then the sensor dataset,
            // so responses are slower than a plain CRUD call.
            .readTimeout(45, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.ROUTING_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SenseNavApi::class.java)
    }
}
