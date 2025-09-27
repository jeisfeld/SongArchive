import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

val firebasePropertiesFile = project.layout.projectDirectory.file("firebase.properties").asFile
val firebaseProperties = Properties()
if (firebasePropertiesFile.exists()) {
    firebasePropertiesFile.inputStream().use(firebaseProperties::load)
}
val firebaseCloudVisionApiKey =
    firebaseProperties.getProperty("firebaseCloudVisionApiKey", "")

val authPropertiesFile = project.layout.projectDirectory.file("auth.properties").asFile
val authProperties = Properties()
if (authPropertiesFile.exists()) {
    authPropertiesFile.inputStream().use(authProperties::load)
}
val basicAuthUsername = authProperties.getProperty("basicAuthUsername", "")
val basicAuthPassword = authProperties.getProperty("basicAuthPassword", "")

android {
    namespace = "de.jeisfeld.songarchive"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.jeisfeld.songarchive"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "1.2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val escapedApiKey = firebaseCloudVisionApiKey
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "FIREBASE_CLOUD_VISION_API_KEY", "\"$escapedApiKey\"")

        val escapedUsername = basicAuthUsername
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val escapedPassword = basicAuthPassword
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "BASIC_AUTH_USERNAME", "\"$escapedUsername\"")
        buildConfigField("String", "BASIC_AUTH_PASSWORD", "\"$escapedPassword\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Jetpack Compose UI
    implementation(libs.ui)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room für SQLite-Datenbank
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.material3)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.androidx.foundation.layout.android)
    ksp(libs.androidx.room.compiler)

    // Retrofit für den HTTP-Download
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)

    // WorkManager für Hintergrund-Download
    implementation(libs.androidx.work.runtime.ktx)

    // Audio player
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    // Nearby Connection
    implementation (libs.play.services.nearby)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
