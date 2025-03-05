plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "de.jeisfeld.songarchive"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.jeisfeld.songarchive"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose UI
    implementation(libs.ui)

    // Room für SQLite-Datenbank
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.material3)
    implementation(libs.androidx.ui.tooling.preview.android)
    ksp(libs.androidx.room.compiler)

    // Retrofit für den HTTP-Download
    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // Coil für das Laden von Bildern
    implementation(libs.coil.compose)

    // WorkManager für Hintergrund-Download
    implementation(libs.androidx.work.runtime.ktx)
}