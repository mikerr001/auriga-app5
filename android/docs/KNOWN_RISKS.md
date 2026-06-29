# Known Risks / Likely First-Build Issues

This document was updated after a live web-research pass specifically aimed
at predicting real build failures before the first GitHub Actions run, given
the actual dependency set in this project. Several genuine, confirmed risks
were found and already fixed in this codebase (see "Already fixed" below).
Remaining items are still unverified against a real compiler — this code has
never been compiled, only researched and manually reviewed.

## Already fixed, based on real research (not guesses)

1. **MediaPipe `tasks-genai` is deprecated — migrated to LiteRT-LM.**
   Google's own current documentation states the MediaPipe LLM Inference API
   (`com.google.mediapipe:tasks-genai`) is in maintenance-only mode with an
   explicit recommendation to migrate to LiteRT-LM
   (`com.google.ai.edge.litertlm`). Real-world GitHub issues against
   `tasks-genai` showed a `NoClassDefFoundError` on an internal proto class,
   a `dlopen failed` JNI `.so` resolution failure specifically on a Samsung
   device, and a JDK17 class-file-version mismatch — all matching the shape
   of the original "model failed to load: null" bug this project exists to
   fix. `MindEngine.kt` and `app/build.gradle.kts` were rewritten to use
   LiteRT-LM's `Engine`/`Conversation`/`EngineConfig` Kotlin API instead.

2. **Room + Kotlin entities needs KSP, not `annotationProcessor`.**
   Confirmed via Room's own documentation: Kotlin entities/DAOs need KSP
   (or kapt) — `annotationProcessor` only runs the Java-style processor and
   will not correctly generate `_Impl` classes for Kotlin sources in this
   project's `memory/` package. Fixed: KSP plugin added to both
   `build.gradle.kts` (root) and `app/build.gradle.kts`, Room compiler
   dependency changed from `annotationProcessor(...)` to `ksp(...)`.

3. **First model choice (Gemma3-1B-IT) was GATED on Hugging Face — switched
   to Qwen2.5-0.5B-Instruct.** Gemma3-1B-IT requires an authenticated
   account that has accepted the Gemma license before any file can be
   downloaded, even via direct URL — an anonymous CI `curl` would fail
   every time. It also uses per-device-chipset filenames rather than one
   universal file. Both problems are avoided entirely by using
   `litert-community/Qwen2.5-0.5B-Instruct` instead — confirmed Apache-2.0
   licensed and fully ungated (verified directly against the Hugging Face
   repo listing), with a single real `.litertlm` filename
   (`Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm`) that
   works the same for every device. This was also the original v1 model
   choice, so this is a return to a previously-validated decision after a
   detour, not a novel untested choice.

4. **Device RAM fragmentation in target markets — added a pre-flight check,
   with explicit graceful degradation.** Real market research (Statcounter,
   GSMArena, industry reports) confirmed current-generation budget Android
   phones in Kenya/Nigeria/Ethiopia (Samsung Galaxy A05/A06 series, Tecno,
   Infinix) commonly ship with 4-6GB RAM, but a real, still-circulating
   population of older/lower-tier devices (some as low as 1GB RAM, partly
   via Android Go) remains part of the actual target user base.
   `MindEngine.hasSufficientRam()` checks total device RAM via
   `ActivityManager` before ever attempting to load the on-device model.
   On devices below the threshold (`BuildConfig.ONDEVICE_MODEL_MIN_DEVICE_RAM_BYTES`,
   currently 3GB as a placeholder pending real device testing):
   - The on-device model is never downloaded or loaded (no wasted
     bandwidth/storage, no OOM risk).
   - All other Auriga features — hazard detection, distance/bearing,
     guidance, audio, haptics, place memory — are completely unaffected.
   - The cloud LLM remains available normally if the device is online
     (`AurigaPipeline.askMind()` checks RAM first and routes accordingly,
     with a specific spoken message distinguishing this case from a
     generic failure — see `MindEngineState.OnDeviceUnavailableLowRam`).

## Still-open risks, ranked by probability

1. **MediaPipe Tasks Vision API surface drift.** `ObjectDetectionEngine.kt`
   still uses `com.google.mediapipe:tasks-vision:0.10.21` for object
   detection (this part was NOT affected by the tasks-genai deprecation —
   Tasks Vision and Tasks GenAI are separate APIs with separate maintenance
   status). MediaPipe's API has changed method/builder names across
   versions before; if the build fails here, check the exact installed
   version's javadoc for the real builder method names and adjust.

2. **LiteRT-LM artifact version pinning.** `app/build.gradle.kts` uses
   `com.google.ai.edge.litertlm:litertlm-android:latest.release` rather
   than a pinned version number, specifically because no reliable current
   version number could be confirmed without risking a wrong guess that
   would itself break the build. `latest.release` resolves safely but
   means builds are not fully reproducible over time — pin to a specific
   version once you've confirmed a working one.

3. **`BuildConfig` fields referenced before sync.** `CloudMindClient.kt` and
   `ModelDownloadManager.kt` reference fields like `BuildConfig.CLOUD_LLM_ENDPOINT`
   and `BuildConfig.ONDEVICE_MODEL_HF_REPO`. These are generated from the
   `buildConfigField(...)` calls in `app/build.gradle.kts` —
   `buildFeatures { buildConfig = true }` is set, which should be
   sufficient, but double check after first sync.

4. **CameraX / MediaPipe Image type bridging.** `ImageConversion.kt` does a
   YUV_420_888 -> NV21 -> JPEG -> Bitmap round trip, which works but is not
   the most efficient path. Chosen for implementation simplicity/correctness
   over performance for MVP v1. If frame rate is too low on real devices,
   replace with a direct YUV->RGB conversion.

5. **Gradle/AGP/Kotlin/KSP version compatibility.** Pinned versions: AGP
   8.5.2, Kotlin 1.9.24, KSP 1.9.24-1.0.20, Gradle 8.5 (wrapper),
   compileSdk/targetSdk 34. KSP version numbers must match the Kotlin
   version's first two components exactly (e.g. Kotlin 1.9.24 needs a KSP
   version starting "1.9.24-") — this pairing was chosen for that reason,
   but Android tooling moves fast; check this combination first if
   `gradlew` fails before reaching your code.

6. **`gradle-wrapper.jar` provenance.** Copied from a prior working AURIGA
   project rather than freshly generated (no network access available to
   download Gradle's official wrapper jar in the build environment). It is
   a real, valid jar (verified as a valid zip archive). Regenerate yourself
   once you have the project locally for full certainty:
   `gradle wrapper --gradle-version 8.5`.

7. **No `.tflite` object detection model bundled.** The app will compile
   but `ObjectDetectionEngine.initialize()` will fail at runtime with a
   clear logged error (not a silent crash) until a real model file is
   placed at `app/src/main/assets/models/object_detector.tflite`.

8. **On-device LLM model download is a ~600MB CI step.** The build
   workflow now downloads the real Qwen2.5-0.5B `.litertlm` file (cached
   across runs via `actions/cache@v4` so this only happens once per cache
   key, not every build) and bundles it into the APK. This makes the
   resulting debug APK substantially larger than a build without it. If
   you'd rather ship a smaller APK and rely on `ModelDownloadManager`'s
   runtime download path instead, remove the three model-bundling steps
   from the workflow — both paths exist in the codebase; CI bundling is
   additive, not required.

## What was NOT possible to verify

- No compile was performed (no Kotlin compiler available in the build
  environment).
- No unit tests were run (none exist yet — this is flagged as a gap).
- No real device or emulator testing.
- Dependency version numbers (LiteRT-LM, KSP, MediaPipe Tasks Vision) were
  cross-checked against live web search results during this update, which
  is meaningfully more reliable than the original pass's training-data-only
  guesses, but still not the same as a live Maven dependency resolution.

## Recommended first steps after pushing to GitHub

1. Open the Actions tab and watch the `Build Auriga Debug APK` workflow run.
   The first run will take longer than usual (~600MB model download);
   subsequent runs reuse the cached model file.
2. If it fails, read the Gradle error output — it will point at the exact
   file/line. Start with the still-open risks above in order.
3. Once it builds: object detection will not work until a `.tflite` model
   is bundled (the on-device LLM model IS bundled by this workflow, unlike
   in the prior revision of this document). Both failure modes produce
   clear, specific log messages rather than crashing — that's expected,
   and is a deliberate design choice (never swallow a failure silently),
   not a regression.
