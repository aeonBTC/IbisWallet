plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "github.aeonbtc.ibiswallet"
    compileSdk = 35

    defaultConfig {
        applicationId = "github.aeonbtc.ibiswallet"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.0-beta"

        vectorDrawables {
            useSupportLibrary = true
        }
        
        // BDK native library only works reliably on ARM architectures
        // x86/x86_64 emulators have compatibility issues
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
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
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Bitcoin Development Kit
    implementation(libs.bdk.android)
    
    // Security & Storage
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.google.material)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // QR Code
    implementation(libs.zxing.core)
    
    // Camera
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    
    // Tor Network
    implementation(libs.tor.android)
    
    // HTTP Client
    implementation(libs.okhttp)
    
    // BC-UR (Uniform Resources) for animated QR codes (PSBT exchange with hardware wallets)
    implementation(libs.hummingbird)
}
