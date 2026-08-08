package com.example.sensenav.data

import com.example.sensenav.model.Refuge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class RefugeDto(
    val id: Long,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val category: String,
    val sensoryLevel: String,
    val noiseLevel: String,
    val crowdLevel: String,
    val imageUrl: String?
)

interface RefugeApi {

    @GET("/api/refuges")
    suspend fun getRefuges(): List<RefugeDto>

    @GET("/api/refuges/{id}")
    suspend fun getRefugeById(
        @Path("id") id: Long
    ): RefugeDto
}

class RefugeRepository {

    private val api: RefugeApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RefugeApi::class.java)
    }

    suspend fun getRefuges(): List<Refuge> =
        withContext(Dispatchers.IO) {
            api.getRefuges().map { it.toDomain() }
        }

    suspend fun getRefugeDetail(id: String): Refuge =
        withContext(Dispatchers.IO) {
            api.getRefugeById(id.toLong()).toDomain()
        }

    private fun RefugeDto.toDomain(): Refuge =
        Refuge(
            id = id.toString(),
            name = name,
            subtitle = address,
            category = category,
            latitude = latitude,
            longitude = longitude,
            rating = rating,
            sensoryTag = sensoryLevel,
            imageLabel = imageUrl ?: category,
            isSaved = false
        )
}