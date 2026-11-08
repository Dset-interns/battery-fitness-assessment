// The Android build of the web app. It bundles ../ios/iphone-pwa (the app, the logos and the offline pose-AI files) as
// assets and shows them in a WebView, so the whole app, pose tests included, runs with no network.
//
// Before building: python3 ios/iphone-pwa/fetch_vendor.py     (downloads the pose runtime and models, about 34 MB)
// Debug APK:       ./gradlew :webapp:assembleDebug
// Signed release:  see BUILD.md

import java.util.Properties

plugins {
    id("com.android.application")
}

val versionNameValue = "0.2.0"
base.archivesName.set("DSeTConsultancy_BatteryFitness_v$versionNameValue")

android {
    namespace = "com.batteryfitness.webapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.batteryfitness.app"
        minSdk = 29          // Android 10, the device floor in the Concept Note
        targetSdk = 35
        versionCode = 2
        versionName = versionNameValue
    }

    signingConfigs {
        // Release signing comes from keystore.properties (not committed); see BUILD.md.
        val props = rootProject.file("keystore.properties")
        if (props.exists()) {
            create("release") {
                val p = Properties().apply { props.inputStream().use { load(it) } }
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }

    // AGP otherwise adds a block with randomly encrypted dependency metadata, which makes every build of the same source differ.
    // Leaving it out lets an evaluator rebuild a byte-identical APK (ruling C57).
    dependenciesInfo { includeInApk = false; includeInBundle = false }

    // The web app is copied in at build time, so there is one copy of it in the repository.
    sourceSets.getByName("main").assets.directories.add("build/generated/web")
    androidResources { noCompress += listOf("task", "wasm", "mjs") }
}

val copyWebApp = tasks.register<Copy>("copyWebApp") {
    val src = rootProject.file("../ios/iphone-pwa")
    from(src) {
        include("index.html", "manifest.json", "sai-logo.png", "dset_logo.png", "vendor/**")
    }
    into(layout.buildDirectory.dir("generated/web/www"))
    doFirst {
        if (!File(src, "vendor/mediapipe/pose_landmarker_full.task").exists()) {
            throw GradleException("The pose-AI files are missing. Run: python3 ios/iphone-pwa/fetch_vendor.py")
        }
    }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach { dependsOn(copyWebApp) }
// Lint reads the assets directory too, so it must wait for the copy.
tasks.configureEach { if (name.contains("lint", ignoreCase = true)) dependsOn(copyWebApp) }
