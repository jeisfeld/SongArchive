package de.jeisfeld.songarchive.sync

import android.util.Base64
import android.util.Log
import de.jeisfeld.songarchive.BuildConfig

object BasicAuthProvider {
    private const val TAG = "BasicAuthProvider"

    val authorizationHeader: String?
        get() {
            val username = BuildConfig.BASIC_AUTH_USERNAME
            val password = BuildConfig.BASIC_AUTH_PASSWORD
            if (username.isBlank() || password.isBlank()) {
                Log.w(TAG, "Basic authentication credentials are missing.")
                return null
            }
            val credentials = "$username:$password"
            val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            return "Basic $encoded"
        }
}
