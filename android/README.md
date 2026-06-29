# Auriga v2 — Assistive Vision Co-Pilot

Auriga is an offline-first assistive co-pilot for visually impaired users,
built around monocular Virtual Fiducial distance/bearing estimation, hazard
detection with collision prediction, and natural spoken + haptic guidance.

This is a from-scratch rebuild (v2) under the DrakoSanctis umbrella. Earlier
"Sentinel" / "Aero" product flavors have been split off into a separate
future product line (Arael) and are **not** part of this repository.

## What's real vs. what needs follow-up

This codebase was assembled by porting genuine domain logic recovered from
the `auriga-virtual-fiducials` and `auriga-hazard-engine` research
repositories, combined with engines (Guidance, Audio, Haptic, Place Memory,
World Model, Continuous Learning) designed directly from the project's
product-design conversations, since the corresponding repos for those were
empty Replit scaffolds with no real implementation to port.

**Important — this has not been compiled or run.** No Android build
toolchain was available in the environment that produced this code. Treat
the first GitHub Actions run as the real first test, and expect to fix
compile errors. See `docs/KNOWN_RISKS.md` for the specific places most
likely to need a fix.

### Genuinely implemented
- Virtual Fiducial distance estimation (`geometry/VirtualFiducialEngine.kt`)
- Bearing/direction resolution (`bearing/BearingEngine.kt`)
- Hazard taxonomy, scoring, and collision-time prediction (`hazard/`)
- Cross-frame object tracking with velocity estimation, feeding real data
  into hazard collision-time prediction (`vision/ObjectTracker.kt`)
- Per-device camera calibration via real `CameraCharacteristics`, cached,
  with a hardcoded fallback (`camera/CameraCalibrationManager.kt`)
- Speech-to-text voice queries wired to MindEngine (`mind/VoiceQueryEngine.kt`)
- Guidance phrasing, audio priority queue, haptic patterns (`guidance/`, `audio/`, `haptic/`)
- Runtime TTS availability checking with graceful English fallback (`audio/AudioEngine.kt`)
- Translated UI strings for all 25 supported locales (`res/values-*/strings.xml`)
- Place Memory / World Model Lite / Continuous Learning Lite, backed by Room (`memory/`)
- CameraX + MediaPipe ObjectDetector pipeline, continuous/no-button (`camera/`, `vision/`)
- MindEngine hybrid on-device/cloud LLM via LiteRT-LM, with explicit failure
  surfacing, idle-unload lifecycle, and a device-RAM pre-flight check (`mind/`)
- 25-language locale tier system, research-backed (`locale/LocaleSupport.kt`)
- Single, non-conflicting GitHub Actions build workflow

### Explicitly stubbed / requires setup before this works end-to-end
1. **No `.tflite` object detection model is bundled.** Drop a compatible
   model (e.g. EfficientDet-Lite0) at `app/src/main/assets/models/object_detector.tflite`.
   (Note: the on-device *LLM* model, unlike the detection model, IS bundled
   automatically by the CI workflow — see "Building" below.)
2. **Cloud LLM is a placeholder.** `CloudMindClient.kt` has empty endpoint/key
   config and a generic request/response shape that will not match any real
   provider. Wire up a real provider before relying on the cloud fallback.
3. **Camera intrinsics use a fallback by default.** `CameraCalibrationManager`
   reads real `CameraCharacteristics` per-device and caches the result, but
   falls back to placeholder focal length / FOV constants if that read
   fails — distance accuracy depends on real calibration data being
   available on a given device (see the Virtual Fiducial research suite's
   calibration work for the broader intended approach).
4. **Object width estimates are class averages**, not measured per-instance —
   see `TYPICAL_WIDTHS_MM` in `AurigaPipeline.kt`.

## Building

```
./gradlew assembleDebug
```

Or push to `main` / trigger manually — see `.github/workflows/build-debug-apk.yml`.
This repo intentionally has **one** workflow file. The original AURIGA v1
project had four overlapping workflows that all triggered on every push,
which is exactly the conflict that caused build confusion previously —
don't add a second workflow file without removing/disabling this one first.

**Note:** the CI workflow automatically downloads the ~600MB Qwen2.5-0.5B
on-device LLM model and bundles it into the APK before building (cached
across runs, so this only downloads once per cache key). Building locally
with a plain `./gradlew assembleDebug` will NOT do this — the app's
`ObjectDetectionEngine`/`MindEngine` will simply report the model as
unavailable until you either run the CI workflow or manually place the
model file yourself (see `docs/KNOWN_RISKS.md`).

## Package structure

```
com.drakosanctis.auriga
├── core/        Application, foreground service, AurigaPipeline orchestrator
├── camera/      CameraX continuous capture
├── vision/      MediaPipe object detection + image conversion
├── geometry/    Virtual Fiducial distance estimation
├── bearing/     Direction/bearing resolution
├── hazard/      Taxonomy, scoring, collision-time prediction
├── guidance/    Hazard -> spoken instruction phrasing
├── audio/       TTS priority queue, volume escalation, mono detection
├── haptic/      Directional vibration patterns
├── memory/      Place Memory, World Model Lite, Continuous Learning Lite (Room)
├── mind/        Hybrid on-device/cloud LLM (MindEngine, ModelDownloadManager, CloudMindClient)
├── locale/      25-language tier support
└── ui/          MainActivity
```

## Background

This MVP scope follows the development order agreed across the project's
design conversations: Camera -> Object Detection -> Bearing -> Distance ->
Hazard -> Guidance -> Audio -> Haptics, with Place Memory Lite / World Model
Lite / Continuous Learning Lite included from day one (rather than deferred
to v2) per the explicit decision to include "Lite" versions of those three
specifically. The on-device LLM runs via LiteRT-LM with Qwen2.5-0.5B-Instruct
(the same model family validated in the prior version, now using the current
non-deprecated runtime) rather than the deprecated MediaPipe tasks-genai
runtime — see docs/KNOWN_RISKS.md for the full history, including why an
initial attempt to use Gemma3-1B-IT was reverted (it's a gated Hugging Face
repo requiring auth, with no single universal filename across devices;
Qwen2.5-0.5B has neither problem).
