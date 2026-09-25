plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.swipedelete.zero"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.swipedelete.zero"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "4.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Deterministic debug keystore committed to the repo (standard debug
        // password). Keeps the APK signature — and therefore the SHA-1 used by
        // the Google OAuth Android client for Drive backup — stable across CI
        // runners and local machines.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("upload") {
            val keyPath = System.getenv("SDZ_UPLOAD_KEYSTORE")
            if (keyPath != null) storeFile = file(keyPath)
            storePassword = System.getenv("SDZ_UPLOAD_STORE_PASSWORD")
            keyAlias = System.getenv("SDZ_UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("SDZ_UPLOAD_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            if (System.getenv("SDZ_UPLOAD_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // Three flavors abstract the permission model:
    // `fdroid` — MediaStore + SAF only, zero network permissions (air-gapped);
    // `play` and `cloud` — opt-in Photos/Drive backup with network access;
    // `fdroid` remains the offline edition.
    flavorDimensions += "distribution"
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_MANAGE_STORAGE", "false")
            buildConfigField("boolean", "SUPPORTS_DRIVE_BACKUP", "false")
            buildConfigField("boolean", "SUPPORTS_PHOTOS_ARCHIVE", "false")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_MANAGE_STORAGE", "false")
            buildConfigField("boolean", "SUPPORTS_DRIVE_BACKUP", "true")
            buildConfigField("boolean", "SUPPORTS_PHOTOS_ARCHIVE", "true")
        }
        create("cloud") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_MANAGE_STORAGE", "false")
            buildConfigField("boolean", "SUPPORTS_DRIVE_BACKUP", "true")
            buildConfigField("boolean", "SUPPORTS_PHOTOS_ARCHIVE", "true")
        }
    }

    // Play and cloud share the authenticated Google Photos implementation.
    // F-Droid remains the separate offline build.
    sourceSets.getByName("play").java.srcDir("src/cloud/java")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager + Hilt worker integration
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Image / video thumbnail loading (all local, no network module pulled in)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // Media3 for opt-in video playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // DataStore for lightweight settings
    implementation(libs.androidx.datastore.preferences)

    // Dominant-color sampling for the dynamic card backdrop
    implementation(libs.androidx.palette)

    // Backported splash screen so the brand mark shows on API 29+ too
    implementation(libs.androidx.core.splashscreen)

    // SAF document access for non-media (.apk/.zip) deletion
    implementation(libs.androidx.documentfile)

    implementation(libs.kotlinx.coroutines.android)

    // Cloud flavor only: Google Sign-In for the opt-in Drive backup. The
    // fdroid/play flavors never compile against any network-capable library.
    "cloudImplementation"(libs.play.services.auth)
    "playImplementation"(libs.play.services.auth)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Stubs android.net.Uri so pure-JVM tests can build MediaItem fixtures.
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
