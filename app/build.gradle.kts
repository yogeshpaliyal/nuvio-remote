plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI (the release workflow) passes the release key and the version through the environment; a local build without
// them is a debug-signed one.
val keystoreFile = System.getenv("KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "dev.skiff"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.skiff"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    // Material 3 Expressive (MaterialExpressiveTheme, flexible app bars, toggle buttons, loading indicator, shapes):
    // public only in 1.5; alpha18 is the last one for compileSdk 36 and AGP 8 (alpha19+ need 37 and AGP 9.1).
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    // Posters and backdrops from Cinemeta.
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Talks ADB to the TV over Wi-Fi, from the phone (the same "am start" you'd run from a computer).
    implementation("dev.mobile:dadb:1.2.10")
}
