package de.jeisfeld.songarchive.firebase

import android.util.Base64
import com.google.gson.annotations.SerializedName
import de.jeisfeld.songarchive.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

internal class FirebaseCloudVisionClient(
    private val service: FirebaseCloudVisionService = defaultService,
    private val apiKey: String = BuildConfig.FIREBASE_CLOUD_VISION_API_KEY,
) {

    class MissingApiKeyException : IllegalStateException("Missing Firebase Cloud Vision API key.")

    suspend fun recognizeHandwrittenText(imageBytes: ByteArray): String? {
        if (apiKey.isBlank()) {
            throw MissingApiKeyException()
        }
        val request = CloudVisionRequest(
            requests = listOf(
                AnnotateImageRequest(
                    image = CloudVisionImage(content = Base64.encodeToString(imageBytes, Base64.NO_WRAP)),
                    features = listOf(CloudVisionFeature(type = "DOCUMENT_TEXT_DETECTION")),
                    imageContext = CloudVisionImageContext(
                        languageHints = listOf("en-t-i0-handwrit", "de-t-i0-handwrit", "en", "de"),
                    ),
                ),
            ),
        )
        val response = service.annotate(request, apiKey)
        return response.responses
            .firstOrNull()
            ?.fullTextAnnotation
            ?.text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private val defaultService: FirebaseCloudVisionService by lazy {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            Retrofit.Builder()
                .baseUrl("https://vision.googleapis.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FirebaseCloudVisionService::class.java)
        }
    }
}

internal interface FirebaseCloudVisionService {
    @POST("v1/images:annotate")
    suspend fun annotate(
        @Body request: CloudVisionRequest,
        @Query("key") apiKey: String,
    ): CloudVisionResponse
}

internal data class CloudVisionRequest(
    val requests: List<AnnotateImageRequest>,
)

internal data class AnnotateImageRequest(
    val image: CloudVisionImage,
    val features: List<CloudVisionFeature>,
    @SerializedName("imageContext")
    val imageContext: CloudVisionImageContext? = null,
)

internal data class CloudVisionImage(
    val content: String,
)

internal data class CloudVisionFeature(
    val type: String,
    @SerializedName("maxResults")
    val maxResults: Int? = null,
)

internal data class CloudVisionImageContext(
    @SerializedName("languageHints")
    val languageHints: List<String>? = null,
)

internal data class CloudVisionResponse(
    val responses: List<AnnotateImageResponse> = emptyList(),
)

internal data class AnnotateImageResponse(
    @SerializedName("fullTextAnnotation")
    val fullTextAnnotation: FullTextAnnotation? = null,
)

internal data class FullTextAnnotation(
    val text: String? = null,
)
