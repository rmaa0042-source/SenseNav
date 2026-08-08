package com.example.sensenav.data

import com.example.sensenav.model.WarningInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class WarningDto(
    val id: Long,
    val title: String,
    val location: String,
    val description: String,
    val dataSource: String,
    val suggestedAction: String,
    val densityPercent: Int,
    val routeId: String
)

interface WarningApi {

    @GET("/api/warnings")
    suspend fun getWarnings(): List<WarningDto>
}

class WarningRepository {

    private val api: WarningApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WarningApi::class.java)
    }

    suspend fun getWarnings(): List<WarningInfo> =
        withContext(Dispatchers.IO) {
            api.getWarnings().map { it.toDomain() }
        }

    private fun WarningDto.toDomain(): WarningInfo =
        WarningInfo(
            id = id.toString(),
            title = title,
            locationName = location,
            densityPercent = densityPercent,
            riskSummary = description,
            dataSource = dataSource,
            suggestedAction = suggestedAction,
            routeId = routeId
        )
}