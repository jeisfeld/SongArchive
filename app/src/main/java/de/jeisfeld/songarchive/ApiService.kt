package de.jeisfeld.songarchive

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface ApiService {
    @GET("download_data.php")
    suspend fun fetchAllData(): SyncResponse
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

data class SongResponse(
    val id: String,
    val title: String,
    val lyrics: String?,
    val author: String?,
    val keywords: String?,
    val tabfilename: String?,
    val mp3filename: String?,
    val mp3filename2: String?
)

data class MeaningResponse(
    val id: Int,
    val title: String,
    val meaning: String
)

data class SongMeaningResponse(
    val song_id: String,
    val meaning_id: Int
)

data class SyncResponse(
    val songs: List<SongResponse>,
    val meanings: List<MeaningResponse>,
    val song_meanings: List<SongMeaningResponse>
)

