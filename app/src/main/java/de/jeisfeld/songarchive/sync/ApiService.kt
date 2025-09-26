package de.jeisfeld.songarchive.sync

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("admin/download_data.php")
    suspend fun fetchAllData(@Query("user") user: String? = null): SyncResponse

    @GET("check_update.php")
    suspend fun checkUpdate(): CheckUpdateResponse
}

object RetrofitClient {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                BasicAuthProvider.authorizationHeader?.let { header ->
                    requestBuilder.addHeader("Authorization", header)
                }
                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://heilsame-lieder.de/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    val httpClient: OkHttpClient
        get() = okHttpClient
}

