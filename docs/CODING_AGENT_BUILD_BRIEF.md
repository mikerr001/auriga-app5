# Auriga v2 — Coding Agent Build Brief

**Audience:** An autonomous coding agent (Replit Agent, Trae, Bolt, or similar) continuing development of an existing, partially-complete Android codebase.

**Status:** This repository contains a complete architectural skeleton with real, hand-written logic for several core engines. It has NOT been compiled or run. Your first job is to get it building; your second job is to complete the explicitly stubbed pieces listed in Part 3.

---

## Part 1 — What Auriga Is

Auriga is an offline-first **assistive vision co-pilot** for blind and visually impaired users, built for Android. It is not a general-purpose AI assistant that happens to have a camera feature — it is a navigation/safety co-pilot first, with conversational AI as a secondary, optional layer.

### The core distinction that shapes every design decision

An **assistant** answers "how can I help you today?" — broad, user-initiated, conversational.

A **co-pilot** answers "how can I help you move through the world more confidently right now?" — narrow, continuous, safety-first, low-latency, and largely autonomous (it doesn't wait to be asked).

Every feature in this app should be evaluated against: *does this help someone navigate more safely and independently, right now, without requiring them to ask?* Conversational/general-knowledge features are real and valuable, but secondary — they should never compromise the reliability of the safety-critical path (hazard detection, distance, bearing, guidance).

### Target users and market context (this shapes the language/locale work specifically)

- ~90% of the world's blind/visually-impaired population lives in low- and middle-income countries.
- India alone holds roughly 20% of the global blind population. South Asia overall carries the single largest regional burden.
- Sub-Saharan Africa (Nigeria, Ethiopia, DRC, East Africa) carries a large and growing burden, and is the area where mainstream accessibility products (Seeing AI, Be My Eyes, built-in phone assistants) have the *weakest* language coverage — this is Auriga's primary strategic opening, not an afterthought.
- The product's home market priority is **East Africa first** (Kenya/Swahili as the anchor), expanding to South Asia and West Africa.

### What Auriga is NOT (explicitly out of scope for this build)

- Not a SLAM/3D-reconstruction system. "World Model" is intentionally lightweight ("Lite") — current room, known exits, recent hazards. Nothing more.
- Not an on-device model-retraining system. "Continuous Learning" means structured correction logging for later human review, never automatic weight updates or behavior changes.
- Not multi-product. Sentinel/Aero (industrial/drone-adjacent product flavors) have been split into a separate future product line ("Arael") and must not be reintroduced into this codebase.
- Not reliant on specialized hardware. The explicit philosophy is "it works with what you already have" — a phone, optionally with earbuds. Smart glasses / chest cameras / custom hardware are multi-year-future scope, not part of this build.

---

## Part 2 — Architecture As Built

### Package structure (`com.drakosanctis.auriga`)

```
core/      Application class, foreground service, AurigaPipeline (the central orchestrator)
camera/    CameraX continuous capture (no manual shutter — frames flow automatically)
vision/    MediaPipe Tasks Vision ObjectDetector wrapper + YUV->Bitmap conversion
geometry/  Virtual Fiducial monocular distance estimation (pinhole camera model)
bearing/   Left/right direction resolution from bounding box or bearing angle
hazard/    Taxonomy (hazard classes), scoring (severity/probability/risk), prediction (time-to-collision)
guidance/  Converts hazard+bearing+distance into spoken instruction text + haptic pattern codes
audio/     TTS priority queue: CRITICAL interrupts, ELEVATED/ROUTINE queue, volume escalates with urgency
haptic/    Directional vibration patterns (left/right/center, routine vs. critical)
memory/    Place Memory Lite, World Model Lite, Continuous Learning Lite — all backed by Room
mind/      Hybrid on-device (MediaPipe LLM Inference) + cloud (placeholder) conversational layer
locale/    25-language tier system for TTS/UI localization
ui/        MainActivity (entry point)
```

### The data flow (this is the actual runtime pipeline, implemented in `core/AurigaPipeline.kt`)

```
CameraX continuous frame capture
        ↓
YUV_420_888 -> Bitmap conversion (vision/ImageConversion.kt)
        ↓
MediaPipe ObjectDetector.detect() -> List<DetectedObject>
        ↓
For each detection:
  ├─ VirtualFiducialEngine.estimateDistance()  [pinhole model: D = (S_mm × f_px) / W_px / 1000]
  ├─ BearingEngine.bearingFromPixelOffset() + resolveDirection()
  ├─ HazardTaxonomy.getClassDef() -> severity base, isMobile, requiresImmediateAttention
  ├─ HazardScoringEngine.scoreHazard() -> severity, probability, confidence, riskScore
  └─ HazardPredictionEngine.predict() -> time-to-collision (only fires if velocity is known)
        ↓
GuidanceEngine.describeHazard() -> spoken text + urgency level + haptic pattern code
        ↓
        ├─ AudioEngine.enqueue()        -> TTS, priority-queued, volume scales with urgency
        └─ HapticEngine.playFromGuidanceCode() -> directional vibration
        ↓
WorldModelLite.recordHazardObservation()  (short-term working memory)
PlaceMemoryRepository.recordObjectObservation()  (long-term, if a place is set)
```

### Why these specific technical choices were made

- **CameraX over raw Camera2**: the prior version of this app (v1) used raw Camera2 directly and was fragile across different OEM camera implementations. CameraX abstracts that away.
- **MediaPipe Tasks Vision over a raw YOLO/TFLite interpreter wrapper**: MediaPipe handles GPU/CPU/NNAPI delegate selection per-device automatically, which matters given the target markets are dominated by budget/mid-range Android phones with wildly inconsistent hardware. It also has Python/C++ runtime equivalents, giving a credible future path to desktop/webcam support without re-architecting.
- **MediaPipe LLM Inference for the on-device model, not a custom TFLite wrapper**: this is what v1 already used (`tasks-genai`), and v1's actual bug was an error-handling defect (silently swallowing the real load failure), not a wrong choice of runtime. This rebuild fixes the error handling rather than replacing the runtime.
- **Qwen2.5-0.5B-Instruct (q8, ~519MB) as the on-device model**: this was already validated in v1 against the size/RAM tradeoff for target devices. Don't second-guess this choice without a specific reason — it was a deliberate decision, not a default.
- **Room for persistence**: Place Memory / World Model / Continuous Learning need simple structured local storage; Room is the standard Android-recommended choice and avoids hand-rolling SQLite boilerplate.

---

## Part 3 — What You Need To Actually Build (in priority order)

### 3.1 — Get it compiling (highest priority, do this first)

This codebase was written without access to a Kotlin compiler. Expect real compile errors. See `docs/KNOWN_RISKS.md` in this repo for the specific highest-probability failure points (MediaPipe API surface drift, Room kapt/KSP setup, BuildConfig field generation). Work through the GitHub Actions build log methodically — fix one error category at a time, don't try to rewrite broadly in response to the first error.

**Do not** restructure the package layout or rename core classes while fixing compile errors unless absolutely necessary — preserve the architecture described in Part 2 even while fixing syntax/API issues within it.

### 3.2 — Bundle a real object detection model

`vision/ObjectDetectionEngine.kt` expects a `.tflite` model at `app/src/main/assets/models/object_detector.tflite`. None is bundled. Source a model that:
- Detects, at minimum: person, car, bicycle, motorcycle, chair, couch, table, door (door detection specifically may require a custom-trained or fine-tuned model — generic COCO-trained detectors don't reliably detect doors; flag this as a known gap if you can't solve it immediately rather than silently shipping without it)
- Is small enough for the target devices (EfficientDet-Lite0 or similar, not a full YOLOv8 variant)
- Update `AurigaPipeline.mapDetectionLabelToHazardClass()` to match whatever label set your chosen model actually outputs — the current mapping assumes COCO-style labels ("person", "car", "bicycle", etc.) and will silently misclassify everything as `unknown_object` if the model's labels differ.

### 3.3 — Add cross-frame object tracking

This is the single most impactful missing piece. Currently every detection is evaluated independently per-frame with `velocityMs = null`, which means `HazardPredictionEngine`'s time-to-collision logic — the "is this vehicle on a direct collision course" feature that was explicitly requested early in this project's design process — never actually fires.

Build a simple tracker:
1. Match detections frame-to-frame by class + spatial proximity (a basic IoU or centroid-distance match is sufficient for v1 — do not over-engineer this into a full multi-object tracking system like DeepSORT).
2. From matched pairs across frames with known timestamps, compute approach/recede velocity using the change in estimated distance over time.
3. Feed that velocity into `ScoringInput.velocityMs` and `PredictionInput.velocityMs`.
4. Handle the edge cases: object temporarily occluded (don't immediately drop the track), object exits frame (drop the track after N missed frames, not immediately).

### 3.4 — Camera calibration

`AurigaPipeline.kt` currently uses placeholder constants (`ASSUMED_FOCAL_LENGTH_PX = 1400f`, `ASSUMED_HORIZONTAL_FOV_DEGREES = 70f`) for every device. Real distance accuracy depends on per-device camera intrinsics. Options, in order of effort:
1. **Minimum viable**: read `android.hardware.camera2.CameraCharacteristics` for focal length and sensor size at runtime, compute FOV from those rather than hardcoding.
2. **Better**: maintain a small lookup table of known-good calibration profiles for common device models (this is exactly what the project's calibration research phase was building toward — check for any calibration data already produced before this build, e.g. `horizon_results.csv`-style outputs from earlier research phases, and incorporate it if available).
3. **Best**: implement a simple one-time user calibration flow (e.g. "hold your phone so a standard sheet of paper fills the frame at arm's length") to derive focal length empirically per-device.

Do not skip this silently — if you implement only the minimum viable option, say so clearly in your output/commit message rather than presenting placeholder accuracy as production-ready.

### 3.5 — Replace per-class average object widths with something better

`AurigaPipeline.TYPICAL_WIDTHS_MM` uses one fixed width per class (e.g. all "person" detections assumed 450mm wide). This is a rough approximation. If time allows, consider:
- Using bounding box *height* as a secondary signal (a detected "person" bounding box that's unusually tall/narrow vs. wide/short gives a hint about distance/pose that a single width constant misses).
- Tightening per-class ranges using any real measurement data from the project's earlier Virtual Fiducial research phase (look for `horizon_results.csv`, ArUco marker test data, or similar — these may exist as historical project artifacts even if not present in this specific repo).

### 3.6 — Wire up real speech-to-text for voice queries

`ui/MainActivity.kt`'s "Ask Auriga" button currently does nothing functional. Implement:
1. Android's `SpeechRecognizer` (on-device where available) or a push-to-talk capture flow.
2. Route the transcribed text to `MindEngine.generate()`.
3. Speak the response via `AudioEngine`.
4. Respect the "co-pilot not assistant" priority: a voice query should not block or delay an in-flight CRITICAL hazard warning. Check `AudioEngine`'s queue state before treating a query response as immediately speakable.

### 3.7 — Configure the cloud LLM fallback (when ready)

`mind/CloudMindClient.kt` is a deliberate placeholder — empty endpoint, empty API key, generic request/response JSON shape that matches no real provider. When a provider is chosen:
1. Set `CLOUD_LLM_ENDPOINT` / inject the API key via secure CI secrets or local.properties — **never commit a real API key to source control**.
2. Rewrite `buildRequestBody()` and `parseResponse()` to match that provider's actual API contract.
3. Test the fallback path explicitly: on-device model fails or unavailable + device online -> cloud should pick up the conversation seamlessly from the user's perspective.

### 3.8 — Implement the 25-language TTS tier system properly

`locale/LocaleSupport.kt` defines the language list and tier classification (Tier 1 = reliable on-device TTS expected; Tier 2 = variable, needs runtime checking) but does not yet implement the runtime check itself. Required:
1. In `AudioEngine.initialize()`, after `setLanguage()`, check the actual return value against `TextToSpeech.LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` for Tier 2 languages specifically.
2. If a Tier 2 language isn't available on-device, either (a) prompt the user to install additional Android TTS voice data, or (b) fall back to routing that utterance's text through a cloud TTS API if one is configured, or (c) at minimum, gracefully fall back to English with a one-time spoken notice — never fail silently.
3. Build the actual string-resource translation files (`values-sw/strings.xml`, `values-ha/strings.xml`, etc.) for all 25 languages' UI text. This wasn't done in this build pass — only the locale *metadata* (`LocaleSupport.kt`) exists.

---

## Part 4 — Engineering Standards To Maintain

These aren't optional style preferences — they reflect specific lessons from this project's development history.

1. **Never swallow exceptions silently.** The original v1 bug report that kicked off this entire rebuild effort was a `catch (Throwable t) { return false; }` pattern that hid the real cause of a model-loading failure behind a generic "model failed to load: null" message, costing significant debugging time. Every catch block in this codebase logs the real `Throwable` and produces a specific, human-readable failure reason. Maintain this pattern in anything you add — if you catch an exception, either handle it meaningfully or log it with full context before doing anything else.

2. **Respect the load-on-demand / unload-on-idle lifecycle for the on-device LLM.** Phones are not LLM servers. `MindEngine` loads the model only when needed and unloads it after a period of inactivity (`idleUnloadMs`). Do not "fix" perceived latency by changing this back to load-at-startup-keep-forever — that reintroduces the RAM/battery pressure problem this design explicitly solves.

3. **One GitHub Actions workflow file, not several.** The original v1 repository had four separate workflow files that all triggered on every push, fighting each other. This repository has exactly one (`.github/workflows/build-debug-apk.yml`). If you need a different build configuration (e.g. release builds, signed APKs), extend the existing workflow with additional jobs/steps rather than adding a second triggering workflow file.

4. **Silent-by-default audio.** Auriga does not narrate everything it sees. Speech only happens for genuine hazards, instructions, or direct responses to the user. If you're tempted to add more spoken output for "helpfulness," check it against this principle first — over-narration is a known failure mode for this category of app and actively reduces trust/usability for the target users.

5. **MVP-v1 scope discipline.** Place Memory, World Model, and Continuous Learning are explicitly "Lite" by design decision, not by accident or laziness. Do not expand them into full SLAM, persistent semantic maps, or on-device model retraining without an explicit, separate decision to do so — that was deliberately deferred as a v2+/long-term-roadmap concern.

6. **Accessibility-first UI, even for the minimal debug UI.** Large touch targets, high contrast, proper `contentDescription` attributes, TalkBack compatibility. The people using this app cannot see a beautifully designed but inaccessible interface.

---

## Part 5 — Testing Expectations

No automated tests exist yet in this codebase — flagged as a known gap, not an oversight to silently leave unaddressed. At minimum, before considering any of Part 3's items "done":

- Unit tests for `VirtualFiducialEngine`, `BearingEngine`, `HazardScoringEngine`, and `HazardPredictionEngine` — these are pure functions with no Android dependencies, so they're trivial to unit test and have zero excuse not to be tested. Use known input/output pairs (e.g. from the project's earlier calibration research data if available) as test fixtures where possible, rather than only testing against arbitrary made-up numbers.
- A manual device-testing checklist covering: bright daylight, low light, indoor/outdoor transitions, and at least 3 distinct real Android devices spanning budget/mid-range/flagship tiers, since device fragmentation is a named, explicit risk for this app's target market.

---

## Part 6 — Reporting Back

When you've made progress, summarize clearly:
- What now compiles and runs vs. what's still broken.
- Which Part 3 items were completed vs. deferred, and why.
- Any architectural deviations you made from this brief, with reasoning — don't silently diverge from documented decisions (e.g. don't quietly swap the on-device model, don't quietly add a second CI workflow) without flagging it explicitly.
