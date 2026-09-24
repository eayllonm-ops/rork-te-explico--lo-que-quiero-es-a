/**
 * Reads a value from the project .env so keys and service URLs are configured
 * at build time instead of being committed to source.
 */
fun envValue(key: String): String {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return ""
    return envFile.readLines()
        .firstOrNull { it.trim().startsWith("$key=") }
        ?.substringAfter("=")
        ?.trim()
        ?.trim('"')
        .orEmpty()
}

val mapsApiKey: String = envValue("EXPO_PUBLIC_GOOGLE_MAPS_API_KEY")
val backendUrl: String = envValue("EXPO_PUBLIC_RORK_FUNCTIONS_URL").trimEnd('/')

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rork.ananego"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rork.ananego"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

configurations.configureEach {
    // Maps Compose pulls newer AndroidX core builds that require compileSdk 37.
    resolutionStrategy {
        force("androidx.core:core-ktx:1.17.0")
        force("androidx.core:core:1.17.0")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.koin.androidx.compose)
    implementation(libs.maps.compose)
    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.ui.tooling)
}
