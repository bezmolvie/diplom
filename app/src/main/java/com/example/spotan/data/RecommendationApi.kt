package com.example.spotan.data

import retrofit2.http.GET
import retrofit2.http.Query

data class Recommendation(
    val track_key: String,
    val score: Float
)

data class RecommendationResponse(
    val recommendations: List<Recommendation>
)

interface RecommendationApi {
    @GET("recommendations")
    suspend fun getRecommendations(
        @Query("track_key") trackKey: String,
        @Query("alpha") alpha: Float = 0.5f,
        @Query("top_k") topK: Int = 5
    ): RecommendationResponse
}
