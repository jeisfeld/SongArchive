package de.jeisfeld.songarchive

import de.jeisfeld.songarchive.db.Song
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface ApiService {
    @GET("download_songs.php")
    suspend fun fetchSongs(): List<Song>
}

object RetrofitClient {
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://jeisfeld.de/songarchive/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
