package de.jeisfeld.songarchive.sync

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("download_data.php")
    suspend fun fetchAllData(@Query("user") user: String? = null): SyncResponse

    @GET("check_update.php")
    suspend fun checkUpdate(): CheckUpdateResponse
}

object RetrofitClient {
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://heilsame-lieder.de/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

