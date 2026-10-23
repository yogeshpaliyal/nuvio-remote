import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase (Crashlytics, Analytics) needs the project's app/google-services.json (from the Firebase console, or a CI
// secret): without it the app builds and runs, and reports nothing.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// CI (the release workflow) passes the release key and the version through the environment; a local build without
// them is a debug-signed one.
val keystoreFile = System.getenv("KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }

val appVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }

android {
    namespace = "dev.skiff"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.skiff"
        minSdk = 26
        targetSdk = 36
        // version.properties (the Release workflow raises it); VERSION_CODE / VERSION_NAME override it.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: appVersion.getProperty("versionCode").toInt()
        versionName = System.getenv("VERSION_NAME") ?: appVersion.getProperty("versionName")
    }

    // Where it's distributed: github (the APK on GitHub releases) or play (the bundle for Google Play). The same app
    // (dev.skiff); what differs is in src/github and src/play (updates), and BuildConfig.STORE.
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"github\"")
        }
        create("play") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"play\"")
        }
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
        // Beside the release build on one phone: its own package (dev.skiff.debug), name and icon (src/debug/res).
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8: shrinks and obfuscates the code, drops unused resources; Crashlytics uploads the mapping, so its
            // stack traces read as the source.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The languages in res/values-*: Android 13+ lists them under Skiff's own language (Settings → Apps → Skiff).
    androidResources {
        generateLocaleConfig = true
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
    // Crash reports and usage (Settings can turn them off).
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
    // Google Play's in-app updates: the play build only (the github one checks GitHub releases).
    "playImplementation"("com.google.android.play:app-update-ktx:2.1.0")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json, for tests on the JVM (its stub in android.jar does nothing): Cinemeta's answers.
    testImplementation("org.json:json:20240303")
}
