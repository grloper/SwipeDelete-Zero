plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.swipedelete.zero"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.swipedelete.zero"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "2.2.0"

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
    }

    buildTypes {
        release {
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
    // `play`  — may additionally request MANAGE_EXTERNAL_STORAGE, still no network;
    // `cloud` — the ONLY flavor with android.permission.INTERNET, powering the
    //           opt-in Google Drive backup. The air-gap promise holds for the
    //           fdroid/play builds; cloud is a separate, clearly-labelled APK.
    flavorDimensions += "distribution"
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_MANAGE_STORAGE", "false")
            buildConfigField("boolean", "SUPPORTS_DRIVE_BACKUP", "false")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_MANAGE_STORAGE", "true")
            buildConfigField("boolean", "SUPPORTS_DRIVE_BACKUP", "false")
        }
        create("cloud") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_MANAGE_STORAGE", "false")
            buildConfigField("boolean", "SUPPORTS_DRIVE_BACKUP", "true")
        }
    }

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

    // SAF document access for non-media (.apk/.zip) deletion
    implementation(libs.androidx.documentfile)

    implementation(libs.kotlinx.coroutines.android)

    // Cloud flavor only: Google Sign-In for the opt-in Drive backup. The
    // fdroid/play flavors never compile against any network-capable library.
    "cloudImplementation"(libs.play.services.auth)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
