import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { stream -> load(stream) }
    }
}

android {
    namespace = "github.aeonbtc.ibiswallet"
    compileSdk = 36
    val sparkApiKey = providers
        .gradleProperty("SPARK_API_KEY")
        .orElse(providers.environmentVariable("SPARK_API_KEY"))
        .orElse(localProperties.getProperty("SPARK_API_KEY").orEmpty())
        .orElse("")

    defaultConfig {
        applicationId = "github.aeonbtc.ibiswallet"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "4.0-beta"

        vectorDrawables {
            useSupportLibrary = true
        }

        val escapedSparkApiKey = sparkApiKey.get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "SPARK_API_KEY", "\"$escapedSparkApiKey\"")

        // BDK native library only works reliably on ARM architectures
        // x86/x86_64 emulators have compatibility issues
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/fr/acinq/secp256k1/jni/native/**"
        }
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.extensions.configure(JacocoTaskExtension::class) {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.register<JacocoReport>("jacocoUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    sourceDirectories.setFrom(files("${projectDir}/src/main/java"))

    val excludes = listOf(
        "**/R.class", "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/databinding/**",
        "**/ui/**",
        "**/theme/**",
    )
    classDirectories.setFrom(
        fileTree("${layout.buildDirectory.get().asFile}/intermediates/javac/debug/classes") { exclude(excludes) },
        fileTree("${layout.buildDirectory.get().asFile}/tmp/kotlin-classes/debug") { exclude(excludes) },
    )

    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/testDebugUnitTest.exec") },
    )
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
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

    // Liquid Wallet Kit (LWK) - Blockstream's Liquid Network wallet toolkit
    implementation(libs.lwk)
    implementation(libs.jna) { artifact { type = "aar" } }
    implementation(libs.lightning.kmp.core.jvm)
    implementation(libs.bitcoinj.core)

    // Spark SDK - Breez Spark Layer 2 Android bindings
    implementation(libs.spark.sdk.android)

    // Testing
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
}
