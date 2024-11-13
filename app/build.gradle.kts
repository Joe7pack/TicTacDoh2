plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    //alias(libs.plugins.kotlin.kapt)
    id("com.google.gms.google-services")
    //id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.guzzardo.tictacdoh2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guzzardo.tictacdoh2"
        minSdk = 23
        targetSdk = 35

        versionCode = 1
        versionName = "1.0"

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
    buildToolsVersion = "35.0.0"
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
    implementation(libs.androidx.appcompat)
    //implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.androidx.multidex)
    implementation(libs.play.services.location)

    // Import the Firebase BoM
    implementation(platform(libs.firebase.bom))

    // When using the BoM, you don't specify versions in Firebase library dependencies

    // Add the dependency for the Firebase SDK for Google Analytics
    implementation(libs.firebase.analytics)
    implementation(libs.androidx.appsearch)
    // Use annotationProcessor instead of kapt if writing Java classes
    //kapt("androidx.appsearch:appsearch-compiler:$appsearch_version")
    implementation(libs.androidx.appsearch.local.storage)
    // PlatformStorage is compatible with Android 12+ devices, and offers additional features
    // to LocalStorage.
    implementation(libs.androidx.appsearch.platform.storage)
    implementation(fileTree(mapOf(
        "dir" to "libs",
        "include" to listOf("*.aar", "*.jar")
        //"exclude" to listOf()
    )))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    //apply(plugin = "com.google.gms.google-services")

    implementation(project(":license"))
    //api("license")
}