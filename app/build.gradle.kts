plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.drakosanctis.auriga"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.drakosanctis.auriga"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0-mvp"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ONDEVICE_MODEL_HF_REPO", "\"litert-community/Qwen2.5-0.5B-Instruct\"")
        buildConfigField("String", "ONDEVICE_MODEL_FILENAME", "\"mind_model.tflite\"")
        buildConfigField("long", "ONDEVICE_MODEL_MIN_BYTES", "200_000_000L")
        buildConfigField("long", "ONDEVICE_MODEL_EST_BYTES", "520_000_000L")
        buildConfigField("long", "ONDEVICE_MODEL_MIN_DEVICE_RAM_BYTES", "3_000_000_000L")
        buildConfigField("String", "CLOUD_LLM_ENDPOINT", "\"https://api.placeholder.invalid/v1/chat\"")
        buildConfigField("String", "CLOUD_LLM_API_KEY", "\"\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Configure Kotlin JVM target via tasks — correct approach for Kotlin 2.x where
// kotlinOptions.jvmTarget (String) was removed and android.compilerOptions is
// not available in AGP 8.10.1.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // CameraX — chosen for broad device/OEM compatibility vs raw Camera2
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // MediaPipe Tasks Vision — on-device object detection
    implementation("com.google.mediapipe:tasks-vision:0.10.21")

    // LiteRT-LM — on-device small-model text generation.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")

    // Networking for model download + cloud LLM fallback
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Local persistence — Room with KSP
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
