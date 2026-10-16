plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mathra.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mathra.keyboard"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Add your own signingConfig here before publishing (see README).
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Intentionally ZERO third-party dependencies: smaller APK, no hidden SDKs, nothing that can phone home.
dependencies { }
