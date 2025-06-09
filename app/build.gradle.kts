plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.artificialinsightsllc.teamsync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.artificialinsightsllc.teamsync"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "6.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Compose UI Additions
    // Material Icons Extended for additional icons (e.g., Visibility, VisibilityOff)
    implementation("androidx.compose.material:material-icons-extended:1.6.7") // Added version

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Compose Material3 (for modern Material Design components)
    implementation("androidx.compose.material3:material3:1.3.2")

    // Image loading library (Coil is recommended for Compose)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil:2.6.0")



    // UCrop
    implementation("com.github.yalantis:ucrop:2.2.8")
    // Image compression
    implementation("id.zelory:compressor:3.0.1")

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.14.0"))

    // Firebase Auth
    implementation("com.google.firebase:firebase-auth-ktx")
    // Firebase Firestore
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Firebase Storage
    implementation("com.google.firebase:firebase-storage-ktx")

    // Required permissions
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Google Maps Compose Utilities
    implementation("com.google.maps.android:maps-compose:4.3.0") // Or the latest stable version
    implementation("com.google.android.gms:play-services-maps:19.2.0") // Or the latest stable version

    // Google Location Services (FusedLocationProviderClient)
    implementation("com.google.android.gms:play-services-location:21.3.0") // Or the latest stable version

    // For logging (if not already present)
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

}
apply(plugin = "com.google.gms.google-services")