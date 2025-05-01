package com.example.spotan.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("http://192.168.1.74:8000")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val recommendationApi: RecommendationApi by lazy {
        retrofit.create(RecommendationApi::class.java)
    }
}
