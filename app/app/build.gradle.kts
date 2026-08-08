// Imported explicitly: inside a Gradle Kotlin DSL script `java` resolves to the
// JavaPluginExtension, which shadows the java.* package.
import java.time.LocalDate

plugins {
    // AGP 9 provides Kotlin support itself; applying kotlin.android here is an
    // error, not a redundancy.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.batteryfitness.app"
    // Current AndroidX requires compiling against 37. This is independent of
    // minSdk: the app still installs and runs on Android 10, which is the
    // device floor Concept Note §7.3 specifies.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.batteryfitness.app"
        // Android 10 — the target device floor in Concept Note §7.3
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // Submission §2.2 — the About screen must show version, build date and
        // team name. The APK *filename* must also carry team + version; that is
        // done at packaging time (see BUILD.md) rather than by fighting AGP 9's
        // variant-output API:
        //   DSeTConsultancy_BatteryFitness_v0.1.0.apk
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now()}\"")
        buildConfigField("String", "TEAM_NAME", "\"DSeT Consultancy\"")
    }

    buildTypes {
        release {
            // Submission §3.6 — dead code and debug logging out of the release build
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Export the Room schema as JSON. Submission §4.2.1 requires the database
// schema as a deliverable, and this generates it from the source of truth
// rather than from a hand-drawn diagram that drifts.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Local storage. SQLCipher is added in Phase 3 alongside encrypted capture.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Camera. CameraX is AndroidX, not Play Services — see the C8 note below.
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-video:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")

    // NOTE (constraint C8, Submission §2.1): the APK must not require Google
    // Play Services. No Firebase, no unbundled ML Kit, no ARCore, no Play
    // Integrity. MediaPipe tasks-vision is a self-contained AAR and is added
    // in Phase 4.
    //
    // This also rules out FusedLocationProviderClient for the §6.3 GPS tag —
    // it ships in Play Services. We use the platform LocationManager instead.

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
}
