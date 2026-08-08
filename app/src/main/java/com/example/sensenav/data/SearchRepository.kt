package com.example.sensenav.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface SearchApi {

    @GET("/api/search")
    suspend fun searchRefuges(
        @Query("keyword") keyword: String
    ): List<RefugeDto>
}

class SearchRepository {

    private val api: SearchApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SearchApi::class.java)
    }

    suspend fun search(keyword: String) =
        withContext(Dispatchers.IO) {
            api.searchRefuges(keyword)
        }
}