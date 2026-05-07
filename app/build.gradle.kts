plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.nutrivisionary"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.nutrivisionary"
        minSdk = 24
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)

    // CameraX — exclude the standalone Guava jar to fix ListenableFuture conflict
    implementation(libs.camera.core) {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation(libs.camera.camera2) {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation(libs.camera.lifecycle) {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation(libs.camera.view) {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation(libs.camera.extensions) {
        exclude(group = "com.google.guava", module = "guava")
    }

    // Guava — single source of truth for ListenableFuture
    implementation("com.google.guava:guava:33.3.1-android")

    // Networking
    implementation("com.android.volley:volley:1.2.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}