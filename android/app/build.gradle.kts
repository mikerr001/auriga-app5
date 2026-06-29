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

        // On-device model config for LiteRT-LM (.litertlm format, not the old
        // .task format used by the deprecated tasks-genai runtime).
        // Qwen2.5-0.5B-Instruct (q8) is used rather than Gemma3-1B: it is
        // Apache-2.0 licensed and UNGATED on Hugging Face (confirmed via the
        // litert-community repo listing) — no account login, no license
        // click-through, no auth token required to download. Gemma3-1B-IT
        // was tried first but is a gated repo requiring an authenticated
        // request, and additionally uses per-device-SoC filenames rather
        // than one universal file — both are unnecessary complications
        // Qwen2.5-0.5B avoids entirely while still fitting comfortably
        // within the 4-6GB RAM budget Android devices (Samsung Galaxy
        // A-series, Tecno, Infinix) that dominate the target markets.
        buildConfigField("String", "ONDEVICE_MODEL_HF_REPO", "\"litert-community/Qwen2.5-0.5B-Instruct\"")
        buildConfigField("String", "ONDEVICE_MODEL_FILENAME", "\"Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm\"")
        buildConfigField("long", "ONDEVICE_MODEL_MIN_BYTES", "400_000_000L")
        buildConfigField("long", "ONDEVICE_MODEL_EST_BYTES", "600_000_000L")
        // Minimum total device RAM (bytes) before the on-device model is
        // even attempted. Devices below this skip on-device mode entirely —
        // all other Auriga features (hazard detection, guidance, audio,
        // haptics) remain fully functional; only the LLM conversational
        // layer is unavailable, with the cloud LLM still reachable if the
        // device is online. This directly addresses budget-device RAM
        // fragmentation in the target markets — see docs/KNOWN_RISKS.md.
        buildConfigField("long", "ONDEVICE_MODEL_MIN_DEVICE_RAM_BYTES", "3_000_000_000L")

        // Placeholder cloud LLM endpoint — wire up real provider/key later.
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

    kotlinOptions {
        jvmTarget = "17"
        // litertlm:latest.release (0.13.1) was compiled with Kotlin 2.3 metadata;
        // this flag allows our 2.1 compiler to read it without a fatal version error.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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

    // MediaPipe Tasks Vision — on-device object detection (cross-device delegate handling)
    implementation("com.google.mediapipe:tasks-vision:0.10.21")

    // LiteRT-LM — on-device small-model text generation. NOTE: this replaces
    // com.google.mediapipe:tasks-genai (MediaPipe LLM Inference API), which
    // Google has placed into maintenance-only mode with an explicit
    // recommendation to migrate to LiteRT-LM. Real-world tasks-genai issues
    // (NoClassDefFoundError on LlmOptionsProto, JNI .so resolution failures
    // on Samsung devices, JDK17 class-file-version mismatches) match the
    // exact failure shape of the original "model failed to load: null" bug
    // this project was built to fix, so staying on the deprecated runtime
    // would be reintroducing the same class of risk on day one.
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

    // Networking for model download + cloud LLM fallback
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Local persistence for Place Memory / World Model / Continuous Learning logs.
    // Uses KSP, not annotationProcessor: Room's entities/DAOs in this project
    // are written in Kotlin (memory/ package), and annotationProcessor only
    // runs Room's Java-style processor, which does not correctly generate
    // _Impl classes for Kotlin sources. KSP is also ~2x faster than the
    // alternative kapt path and is Google's recommended path for Kotlin Room.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
