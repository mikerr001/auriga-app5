import { useEffect, useRef, useState, type PointerEvent, type ReactNode } from "react";
import { Camera, Crosshair, Map as MapIcon, Mic, Plus, RotateCcw, Settings2, Target, Trash2, Volume2, VolumeX } from "lucide-react";
import type { ObjectDetection } from "@tensorflow-models/coco-ssd";
import { OBJECT_CLASSES } from "@/lib/auriga/catalog";
import {
  LANGUAGES,
  advanceWorld,
  analyze,
  answerQuestion,
  bearingPhrase,
  clampPose,
  defaultRig,
  formatMeters,
  groundSamples,
  movePose,
  projectWorld,
  quantizeCloud,
  seedWorld,
  type Detection,
  type FrameReport,
  type MapPoint,
  type Pose,
  type Rig,
  type Track,
  type WorldObject,
} from "@/lib/auriga/engine";
import { useAuriga } from "@/lib/auriga/store";
import { paintGuide, paintMap, readInk } from "@/components/auriga/paint";

type Tab = "scanner" | "map" | "targets";
type Source = "sim" | "live";
type Neural = "booting" | "loading" | "ready" | "geometry";

function fitCanvas(canvas: HTMLCanvasElement) {
  const rect = canvas.getBoundingClientRect();
  if (rect.width < 40 || rect.height < 40) return null;
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const w = Math.max(280, Math.round(rect.width * dpr));
  const h = Math.max(180, Math.round(rect.height * dpr));
  if (canvas.width !== w || canvas.height !== h) {
    canvas.width = w;
    canvas.height = h;
  }
  return { w, h };
}

function speak(text: string, lang: string) {
  if (!("speechSynthesis" in window)) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = lang;
  utterance.rate = 1.02;
  window.speechSynthesis.speak(utterance);
}

export function AurigaApp() {
  return <AurigaLive />;
}

function AurigaLive() {
  const settings = useAuriga();
  const [tab, setTab] = useState<Tab>("scanner");
  const [source, setSource] = useState<Source>("sim");
  const [pose, setPose] = useState<Pose>({ x: 0, z: 0, heading: 0 });
  const [report, setReport] = useState<FrameReport | null>(null);
  const [cloudCount, setCloudCount] = useState(0);
  const [anchor, setAnchor] = useState<{ id: string; klass: string; x: number; z: number } | null>(null);
  const [ask, setAsk] = useState("");
  const [answer, setAnswer] = useState("Ask what is ahead, left, right, or overhead.");
  const [sheet, setSheet] = useState(false);
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState("");
  const [note, setNote] = useState("");
  const [picked, setPicked] = useState<string | null>(null);
  const [placeName, setPlaceName] = useState("");
  const [correction, setCorrection] = useState("");
  const [knownMeters, setKnownMeters] = useState("2");
  const [neural, setNeural] = useState<Neural>("booting");
  const [liveNote, setLiveNote] = useState("Practice street is on. Step and turn. The camera is optional.");
  const [listening, setListening] = useState(false);
  const [installEvent, setInstallEvent] = useState<InstallPrompt | null>(null);

  const poseRef = useRef(pose);
  const tabRef = useRef(tab);
  const sourceRef = useRef(source);
  const settingsRef = useRef(settings);
  const objectsRef = useRef<WorldObject[]>(seedWorld());
  const tracksRef = useRef<Map<string, Track>>(new Map());
  const cloudRef = useRef<MapPoint[]>([]);
  const reportRef = useRef<FrameReport | null>(null);
  const liveDetsRef = useRef<Detection[]>([]);
  const pinRef = useRef<Detection | null>(null);
  const detectorRef = useRef<ObjectDetection | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const guideRef = useRef<HTMLCanvasElement | null>(null);
  const mapRef = useRef<HTMLCanvasElement | null>(null);
  const rigRef = useRef<Rig>(defaultRig());
  const audioRef = useRef<AudioContext | null>(null);
  const spokenRef = useRef("");
  const anchorRef = useRef(anchor);

  poseRef.current = pose;
  tabRef.current = tab;
  sourceRef.current = source;
  settingsRef.current = settings;
  anchorRef.current = anchor;

  const nudge = (next: (current: Pose) => Pose) => {
    setPose((current) => {
      const updated = clampPose(next(current));
      poseRef.current = updated;
      return updated;
    });
  };

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target;
      if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) return;
      if (event.key === "a" || event.key === "ArrowLeft") nudge((p) => ({ ...p, heading: p.heading - 8 }));
      if (event.key === "d" || event.key === "ArrowRight") nudge((p) => ({ ...p, heading: p.heading + 8 }));
      if (event.key === "w" || event.key === "ArrowUp") nudge((p) => movePose(p, 0.45));
      if (event.key === "s" || event.key === "ArrowDown") nudge((p) => movePose(p, -0.45));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => {
    let frame = 0;
    let last = performance.now();
    let uiAt = 0;
    let seenAt = 0;
    const loop = (now: number) => {
      const dt = Math.min(0.08, (now - last) / 1000);
      last = now;
      const guide = guideRef.current;
      const map = mapRef.current;
      const mode = tabRef.current;
      const s = settingsRef.current;
      if (mode === "scanner" && guide) {
        const size = fitCanvas(guide);
        if (size) {
          rigRef.current = {
            frameW: size.w,
            frameH: size.h,
            fov: s.fov,
            pitchDownDeg: s.pitch,
            cameraHeight: s.cameraHeight,
            scale: s.scale,
          };
        }
      } else if (mode === "map" && map) {
        fitCanvas(map);
      }
      const rig = {
        ...rigRef.current,
        fov: s.fov,
        pitchDownDeg: s.pitch,
        cameraHeight: s.cameraHeight,
        scale: s.scale,
      };
      rigRef.current = rig;
      let dets: Detection[] = [];
      if (sourceRef.current === "sim") {
        objectsRef.current = advanceWorld(objectsRef.current, dt);
        dets = projectWorld(objectsRef.current, poseRef.current, rig);
      } else {
        dets = [...liveDetsRef.current];
        if (pinRef.current) dets = [pinRef.current, ...dets];
      }
      const analyzed = analyze(dets, rig, poseRef.current, tracksRef.current, now);
      tracksRef.current = analyzed.tracks;
      reportRef.current = analyzed.report;
      const samples = groundSamples(poseRef.current, rig);
      const marks: MapPoint[] = analyzed.report.readings.flatMap((reading) => {
        if (reading.worldX == null || reading.worldZ == null) return [];
        const level = analyzed.report.hazards.find((item) => item.id === reading.id)?.level;
        return [{ x: reading.worldX, z: reading.worldZ, kind: "object" as const, klass: reading.klass, level }];
      });
      cloudRef.current = quantizeCloud(cloudRef.current, [...samples, ...marks]);
      if (guide && mode === "scanner") {
        const ctx = guide.getContext("2d");
        if (ctx) {
          paintGuide(ctx, rig, poseRef.current, analyzed.report, sourceRef.current === "live" ? videoRef.current : null, readInk(guide));
        }
      }
      if (map && mode === "map") {
        const ctx = map.getContext("2d");
        if (ctx) paintMap(ctx, poseRef.current, cloudRef.current, anchorRef.current, readInk(map));
      }
      if (now - uiAt > 180) {
        uiAt = now;
        setReport(analyzed.report);
        setCloudCount(cloudRef.current.length);
      }
      if (now - seenAt > 2500) {
        seenAt = now;
        const names = new Set(s.targets.map((t) => t.name));
        const hit = analyzed.report.readings.find((r) => names.has(r.klass));
        if (hit) s.markSeen(hit.klass, `${bearingPhrase(hit.bearingDeg)}, ${formatMeters(hit.distanceM)}`);
      }
      frame = requestAnimationFrame(loop);
    };
    frame = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(frame);
  }, []);

  useEffect(() => {
    let cancel = false;
    const arm = async () => {
      setNeural("loading");
      try {
        const tf = await import("@tensorflow/tfjs");
        await tf.ready();
        const coco = await import("@tensorflow-models/coco-ssd");
        const model = await coco.load({ base: "lite_mobilenet_v2" });
        if (cancel) {
          model.dispose();
          return;
        }
        detectorRef.current = model;
        setNeural("ready");
        setLiveNote("The camera model is on this phone. Distance still comes from the ground ruler.");
      } catch {
        if (!cancel) {
          setNeural("geometry");
          setLiveNote("The camera model stayed offline. The practice street still measures.");
        }
      }
    };
    const timer = window.setTimeout(() => void arm(), 600);
    return () => {
      cancel = true;
      window.clearTimeout(timer);
    };
  }, []);

  useEffect(() => {
    if (source !== "live" || neural !== "ready") return;
    let stop = false;
    const tick = async () => {
      if (stop) return;
      const video = videoRef.current;
      const detector = detectorRef.current;
      if (video && detector && video.readyState >= 2 && video.videoWidth > 0) {
        try {
          const predictions = await detector.detect(video, 12, 0.45);
          const rig = rigRef.current;
          const scaleX = rig.frameW / video.videoWidth;
          const scaleY = rig.frameH / video.videoHeight;
          liveDetsRef.current = predictions.map((prediction, index) => {
            const [x, y, w, h] = prediction.bbox;
            return {
              id: `${prediction.class}-${index}`,
              klass: prediction.class,
              score: prediction.score,
              x: x * scaleX,
              y: y * scaleY,
              w: w * scaleX,
              h: h * scaleY,
            };
          });
        } catch {
          setNeural("geometry");
        }
      }
      if (!stop) window.setTimeout(tick, 700);
    };
    void tick();
    return () => {
      stop = true;
    };
  }, [neural, source]);

  useEffect(() => {
    const primary = report?.primary;
    if (!primary || !settings.voice || primary.level === "watch") return;
    const key = `${primary.id}:${primary.level}:${Math.round(primary.distanceM ?? 0)}`;
    if (spokenRef.current === key) return;
    spokenRef.current = key;
    speak(report.phrase, settings.language);
    if (settings.haptics && primary.level === "critical" && navigator.vibrate) {
      const left = primary.bearingDeg < -8;
      navigator.vibrate(left ? [40, 70, 40] : primary.bearingDeg > 8 ? [40, 50, 40, 50, 40] : [90, 40, 90]);
    }
  }, [report, settings.haptics, settings.language, settings.voice]);

  useEffect(() => {
    if (!settings.sonar || !report?.primary || report.primary.level === "watch" || report.primary.distanceM == null) return;
    const AudioCtx = window.AudioContext;
    if (!AudioCtx) return;
    if (!audioRef.current) audioRef.current = new AudioCtx();
    const ctx = audioRef.current;
    if (ctx.state === "suspended") void ctx.resume();
    const primary = report.primary;
    let stop = false;
    let timer = 0;
    const beep = () => {
      if (stop) return;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      const pan = ctx.createStereoPanner();
      pan.pan.value = Math.max(-1, Math.min(1, primary.bearingDeg / 40));
      osc.type = "sine";
      osc.frequency.value = primary.signature === "OVERHANG" ? 520 : primary.level === "critical" ? 880 : 640;
      gain.gain.setValueAtTime(0.0001, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.045, ctx.currentTime + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.08);
      osc.connect(gain);
      gain.connect(pan);
      pan.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.09);
      const gap = Math.min(1100, Math.max(160, (primary.distanceM ?? 6) * 170));
      timer = window.setTimeout(beep, gap);
    };
    beep();
    return () => {
      stop = true;
      window.clearTimeout(timer);
    };
  }, [report?.primary, settings.sonar]);

  const startCamera = async () => {
    setLiveNote("Asking for the camera…");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: false,
      });
      if (!videoRef.current) {
        const video = document.createElement("video");
        video.playsInline = true;
        video.muted = true;
        video.autoplay = true;
        videoRef.current = video;
      }
      videoRef.current.srcObject = stream;
      await videoRef.current.play();
      setSource("live");
      setLiveNote(neural === "ready" ? "Camera and model are both live." : "Camera is on. Names appear when the model finishes.");
    } catch {
      setSource("sim");
      setLiveNote("Camera stayed blocked. The practice street is still scanning.");
    }
  };

  const stopCamera = () => {
    const video = videoRef.current;
    const stream = video?.srcObject;
    if (stream instanceof MediaStream) stream.getTracks().forEach((track) => track.stop());
    if (video) video.srcObject = null;
    liveDetsRef.current = [];
    pinRef.current = null;
    setSource("sim");
    setLiveNote("Back on the practice street.");
  };

  const onGuidePointer = (event: PointerEvent<HTMLCanvasElement>) => {
    if (source !== "live") return;
    const canvas = guideRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * canvas.width;
    const y = ((event.clientY - rect.top) / rect.height) * canvas.height;
    pinRef.current = { id: "pin", klass: "marked point", score: 0.8, x: x - 18, y: y - 36, w: 36, h: 36 };
  };

  const resetYard = () => {
    objectsRef.current = seedWorld();
    tracksRef.current = new Map();
    cloudRef.current = [];
    pinRef.current = null;
    const origin = { x: 0, z: 0, heading: 0 };
    poseRef.current = origin;
    setPose(origin);
    setAnchor(null);
    setCloudCount(0);
  };

  const listen = () => {
    const speechWindow = window as Window & {
      webkitSpeechRecognition?: new () => SpeechRec;
      SpeechRecognition?: new () => SpeechRec;
    };
    const Recognition = speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition;
    if (!Recognition) {
      setSheet(true);
      setAnswer("This browser has no speech recognition. Type the question instead.");
      return;
    }
    const recognition = new Recognition();
    recognition.lang = settings.language;
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;
    setListening(true);
    recognition.onresult = (event) => {
      const question = event.results[0]?.[0]?.transcript ?? "";
      setAsk(question);
      const next = answerQuestion(question, reportRef.current);
      setAnswer(next);
      if (settings.voice) speak(next, settings.language);
      setListening(false);
    };
    recognition.onerror = () => setListening(false);
    recognition.onend = () => setListening(false);
    recognition.start();
  };

  useEffect(() => {
    const onPrompt = (event: Event) => {
      event.preventDefault();
      setInstallEvent(event as InstallPrompt);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);
    return () => window.removeEventListener("beforeinstallprompt", onPrompt);
  }, []);


  const primary = report?.primary ?? null;
  const locked = report?.readings.find((r) => settings.targets.some((t) => t.name === r.klass)) ?? null;
  const status = neural === "ready" ? "Eyes ready" : neural === "geometry" ? "Ruler only" : "Waking";
  const filtered = OBJECT_CLASSES.filter((name) => name.includes(search.trim().toLowerCase()));
  const meters = primary?.distanceM != null ? primary.distanceM.toFixed(1) : "—";

  return (
    <div className="flex h-dvh justify-center bg-surface-2">
      <main className="relative flex h-dvh w-full max-w-md flex-col overflow-hidden bg-bg text-fg">
        <header className="flex items-center justify-between px-4 pt-4 pb-2">
          <div>
            <h1 className="font-display text-3xl leading-none">Auriga</h1>
            <p className="text-base text-muted">Phone guide. No store app.</p>
          </div>
          <button
            type="button"
            className="inline-flex size-12 items-center justify-center rounded-2xl bg-fg text-bg"
            aria-label="Open phone settings"
            onClick={() => setSheet(true)}
          >
            <Settings2 className="size-5" />
          </button>
        </header>

        <section className="relative min-h-0 flex-1 px-3">
          <canvas
            ref={guideRef}
            onPointerDown={onGuidePointer}
            className={tab === "scanner" ? "h-full w-full rounded-3xl border border-line bg-surface-2 touch-none" : "hidden"}
            aria-label="Look view with distance marks"
          />
          <canvas
            ref={mapRef}
            className={tab === "map" ? "h-full w-full rounded-3xl border border-line bg-surface touch-none" : "hidden"}
            aria-label="Walking chart"
          />

          {tab === "scanner" && (
            <div className="pointer-events-none absolute inset-x-3 top-3 flex justify-center">
              <span className="rounded-full bg-fg px-3 py-1 text-sm text-bg">{status}</span>
            </div>
          )}

          {tab === "map" && (
            <div className="pointer-events-none absolute inset-x-6 bottom-4 rounded-2xl bg-surface/95 p-3">
              <p className="font-display text-xl">Walking chart</p>
              <p className="text-base text-muted">{cloudCount} points kept on this phone. The arrow is you.</p>
            </div>
          )}

          {tab === "targets" && (
            <div className="absolute inset-0 overflow-y-auto px-4 pt-2 pb-4">
              <div className="flex items-end justify-between">
                <div>
                  <h2 className="font-display text-3xl">Find</h2>
                  <p className="text-base text-muted">{settings.targets.length} things to watch</p>
                </div>
                <button
                  type="button"
                  className="inline-flex size-12 items-center justify-center rounded-2xl bg-cyan text-bg"
                  aria-label="Add something to find"
                  onClick={() => setAdding(true)}
                >
                  <Plus className="size-5" />
                </button>
              </div>
              <ul className="mt-4 flex flex-col gap-3">
                {settings.targets.length === 0 && (
                  <li className="rounded-3xl bg-surface px-4 py-10 text-center text-base text-muted">
                    Nothing to find yet. Add a chair, a door, a person.
                  </li>
                )}
                {settings.targets.map((item) => (
                  <li key={item.id} className="flex items-start justify-between gap-3 rounded-3xl bg-surface p-4">
                    <div className="min-w-0">
                      <p className="truncate text-xl font-bold capitalize">{item.name}</p>
                      <p className="text-base text-muted">{item.note}</p>
                      <p className="mt-1 text-base text-cyan">
                        {item.lastSeenAt ? `Last: ${item.lastWhere ?? "seen"}` : "Not seen yet"}
                      </p>
                    </div>
                    <button
                      type="button"
                      className="inline-flex size-12 shrink-0 items-center justify-center rounded-2xl bg-surface-2 text-fg"
                      aria-label={`Remove ${item.name}`}
                      onClick={() => settings.removeTarget(item.id)}
                    >
                      <Trash2 className="size-5" />
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>

        {tab !== "targets" && (
          <div className="flex flex-col gap-3 px-3 pt-3">
            <div className="rounded-3xl bg-surface p-4">
              <div className="flex items-end justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-base text-muted capitalize">
                    {primary?.level ?? "quiet"} · {rangeWord(primary?.method)}
                  </p>
                  <p className="font-display text-6xl leading-none">
                    {meters}
                    <span className="ml-1 font-sans text-xl text-muted">m</span>
                  </p>
                  <p className="truncate text-lg">
                    {primary ? `${primary.klass}, ${bearingPhrase(primary.bearingDeg)}` : "Step until something shows"}
                  </p>
                </div>
                <button
                  type="button"
                  className="inline-flex size-14 shrink-0 items-center justify-center rounded-2xl bg-cyan text-bg"
                  aria-label={listening ? "Listening" : "Ask with your voice"}
                  onClick={listen}
                >
                  <Mic className="size-6" />
                </button>
              </div>
              <p className="mt-2 text-base" aria-live="polite">
                {report?.phrase ?? "Opening the street."}
              </p>
              {locked && (
                <p className="mt-1 text-base font-bold text-cyan">
                  Finding {locked.klass} · {formatMeters(locked.distanceM)}
                </p>
              )}
            </div>
            {source === "sim" && (
              <div className="grid grid-cols-3 gap-2">
                <button type="button" className="min-h-12 rounded-2xl bg-surface text-base" onClick={() => nudge((p) => ({ ...p, heading: p.heading - 8 }))}>
                  Left
                </button>
                <button type="button" className="min-h-12 rounded-2xl bg-fg text-base font-bold text-bg" onClick={() => nudge((p) => movePose(p, 0.45))}>
                  Step
                </button>
                <button type="button" className="min-h-12 rounded-2xl bg-surface text-base" onClick={() => nudge((p) => ({ ...p, heading: p.heading + 8 }))}>
                  Right
                </button>
              </div>
            )}
          </div>
        )}

        <nav className="grid grid-cols-3 gap-2 px-3 pt-3 pb-3" aria-label="Auriga sections">
          <NavButton active={tab === "scanner"} label="Look" onClick={() => setTab("scanner")}>
            <Camera className="size-5" />
          </NavButton>
          <NavButton active={tab === "map"} label="Chart" onClick={() => setTab("map")}>
            <MapIcon className="size-5" />
          </NavButton>
          <NavButton active={tab === "targets"} label="Find" onClick={() => setTab("targets")}>
            <Target className="size-5" />
          </NavButton>
        </nav>

        {adding && (
          <div className="absolute inset-0 z-30 flex items-end bg-fg/40" role="dialog" aria-label="Add something to find">
            <div className="flex max-h-[85dvh] w-full max-w-md flex-col gap-3 rounded-t-3xl bg-bg p-4">
              <div className="flex items-center justify-between">
                <h2 className="font-display text-3xl">Add</h2>
                <button type="button" className="min-h-12 px-2 text-base" onClick={() => setAdding(false)}>
                  Close
                </button>
              </div>
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search"
                aria-label="Search object classes"
                className="min-h-12 rounded-2xl border border-line bg-surface px-3 text-base"
              />
              <ul className="min-h-0 flex-1 overflow-y-auto rounded-2xl bg-surface">
                {filtered.map((name) => (
                  <li key={name}>
                    <button
                      type="button"
                      className={`flex min-h-12 w-full items-center justify-between px-3 text-left text-base capitalize ${picked === name ? "bg-fg text-bg" : ""}`}
                      onClick={() => setPicked(name)}
                    >
                      {name}
                      {picked === name && <Crosshair className="size-4" />}
                    </button>
                  </li>
                ))}
              </ul>
              <input
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder="Note, optional"
                aria-label="Target note"
                className="min-h-12 rounded-2xl border border-line bg-surface px-3 text-base"
              />
              <button
                type="button"
                disabled={!picked}
                className="min-h-12 rounded-2xl bg-cyan text-base font-bold text-bg disabled:opacity-40"
                onClick={() => {
                  if (!picked) return;
                  settings.addTarget(picked, note);
                  setPicked(null);
                  setNote("");
                  setSearch("");
                  setAdding(false);
                }}
              >
                Watch for this
              </button>
            </div>
          </div>
        )}

        {sheet && (
          <div className="absolute inset-0 z-40 flex justify-center bg-fg/40" role="dialog" aria-label="Phone settings">
            <div className="flex h-full w-full max-w-md flex-col bg-bg">
              <header className="flex items-center justify-between px-4 pt-4 pb-2">
                <h2 className="font-display text-3xl">On this phone</h2>
                <button type="button" className="min-h-12 text-base font-bold text-cyan" onClick={() => setSheet(false)}>
                  Done
                </button>
              </header>
              <div className="flex flex-1 flex-col gap-4 overflow-y-auto px-4 pb-8">
                <p className="text-base">{liveNote}</p>
                <section className="rounded-3xl bg-surface p-4">
                  <h3 className="font-display text-2xl">Install</h3>
                  <p className="mt-1 text-base text-muted">
                    AurigaNavi is only this page. Add it to your home screen. There is no Android app and no second website.
                  </p>
                  {installEvent ? (
                    <button
                      type="button"
                      className="mt-3 min-h-12 w-full rounded-2xl bg-fg text-base font-bold text-bg"
                      onClick={() => {
                        void installEvent.prompt();
                        setInstallEvent(null);
                      }}
                    >
                      Add to home screen
                    </button>
                  ) : (
                    <p className="mt-3 text-base">
                      In the browser menu, choose Add to Home Screen. On iPhone, use Share, then Add to Home Screen.
                    </p>
                  )}
                </section>
                <div className="flex flex-wrap gap-2">
                  {source === "sim" ? (
                    <button type="button" className="min-h-12 rounded-2xl bg-cyan px-4 text-base font-bold text-bg" onClick={() => void startCamera()}>
                      Use camera
                    </button>
                  ) : (
                    <button type="button" className="min-h-12 rounded-2xl bg-surface px-4 text-base" onClick={stopCamera}>
                      Leave camera
                    </button>
                  )}
                  <button type="button" className="inline-flex min-h-12 items-center gap-2 rounded-2xl bg-surface px-4 text-base" onClick={resetYard}>
                    <RotateCcw className="size-4" /> Reset street
                  </button>
                  <button
                    type="button"
                    className="min-h-12 rounded-2xl bg-surface px-4 text-base"
                    onClick={() => {
                      if (!primary || primary.worldX == null || primary.worldZ == null) return;
                      setAnchor({ id: primary.id, klass: primary.klass, x: primary.worldX, z: primary.worldZ });
                    }}
                  >
                    Drop a pin
                  </button>
                  <button type="button" className="min-h-12 rounded-2xl bg-surface px-4 text-base" onClick={() => nudge((p) => movePose(p, -0.45))}>
                    Step back
                  </button>
                </div>
                <div className="grid grid-cols-3 gap-2">
                  <Toggle on={settings.voice} label="Voice" pressed={() => settings.patch({ voice: !settings.voice })} />
                  <Toggle on={settings.haptics} label="Buzz" pressed={() => settings.patch({ haptics: !settings.haptics })} />
                  <Toggle on={settings.sonar} label="Ping" pressed={() => settings.patch({ sonar: !settings.sonar })} />
                </div>
                <label className="text-base text-muted" htmlFor="lang">
                  Spoken language
                </label>
                <select
                  id="lang"
                  value={settings.language}
                  onChange={(event) => settings.patch({ language: event.target.value })}
                  className="min-h-12 rounded-2xl border border-line bg-surface px-3 text-base"
                >
                  {LANGUAGES.map(([code, label]) => (
                    <option key={code} value={code}>
                      {label}
                    </option>
                  ))}
                </select>
                <Slider label="Lens height" value={settings.cameraHeight} min={0.9} max={1.9} step={0.02} unit=" m" onChange={(cameraHeight) => settings.patch({ cameraHeight })} />
                <Slider label="Tilt down" value={settings.pitch} min={0} max={40} step={1} unit="°" onChange={(pitch) => settings.patch({ pitch })} />
                <Slider label="Field of view" value={settings.fov} min={45} max={90} step={1} unit="°" onChange={(fov) => settings.patch({ fov })} />
                <div>
                  <p className="text-base text-muted">If you know how far something really is</p>
                  <div className="mt-2 flex gap-2">
                    <input
                      inputMode="decimal"
                      value={knownMeters}
                      aria-label="Known distance in meters"
                      onChange={(event) => setKnownMeters(event.target.value)}
                      className="min-h-12 w-24 rounded-2xl border border-line bg-surface px-3 text-base"
                    />
                    <button
                      type="button"
                      className="min-h-12 flex-1 rounded-2xl bg-fg text-base font-bold text-bg"
                      onClick={() => {
                        const known = Number(knownMeters);
                        if (!primary?.distanceM || !Number.isFinite(known) || known <= 0) return;
                        const scale = Math.max(0.4, Math.min(2.5, settings.scale * (known / primary.distanceM)));
                        settings.patch({ scale: Number(scale.toFixed(3)) });
                      }}
                    >
                      Use {settings.scale.toFixed(2)}×
                    </button>
                  </div>
                </div>
                <form
                  className="rounded-3xl bg-surface p-4"
                  onSubmit={(event) => {
                    event.preventDefault();
                    const next = answerQuestion(ask, report);
                    setAnswer(next);
                    if (settings.voice) speak(next, settings.language);
                  }}
                >
                  <label htmlFor="ask" className="text-base text-muted">
                    Ask
                  </label>
                  <div className="mt-2 flex gap-2">
                    <input
                      id="ask"
                      value={ask}
                      onChange={(event) => setAsk(event.target.value)}
                      placeholder="What is ahead?"
                      className="min-h-12 min-w-0 flex-1 rounded-2xl border border-line bg-bg px-3 text-base"
                    />
                    <button type="submit" className="min-h-12 rounded-2xl bg-cyan px-4 text-base font-bold text-bg">
                      Ask
                    </button>
                  </div>
                  <p className="mt-2 text-base">{answer}</p>
                </form>
                <div>
                  <h3 className="font-display text-2xl">Places</h3>
                  <div className="mt-2 flex gap-2">
                    <input
                      value={placeName}
                      onChange={(event) => setPlaceName(event.target.value)}
                      placeholder="Kitchen, gate"
                      aria-label="Place name"
                      className="min-h-12 min-w-0 flex-1 rounded-2xl border border-line bg-surface px-3 text-base"
                    />
                    <button
                      type="button"
                      className="min-h-12 rounded-2xl bg-fg px-4 text-base font-bold text-bg"
                      onClick={() => {
                        settings.addPlace({
                          id: `${Date.now()}`,
                          name: placeName.trim() || `Place ${settings.places.length + 1}`,
                          at: Date.now(),
                          note: report?.phrase ?? "Empty frame",
                        });
                        setPlaceName("");
                      }}
                    >
                      Save
                    </button>
                  </div>
                  <ul className="mt-2 flex flex-col gap-2">
                    {settings.places.map((place) => (
                      <li key={place.id} className="flex items-start justify-between gap-2 text-base">
                        <span>
                          <span className="font-bold">{place.name}.</span> <span className="text-muted">{place.note}</span>
                        </span>
                        <button type="button" className="min-h-12 text-cyan" onClick={() => settings.removePlace(place.id)}>
                          Remove
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
                <div>
                  <h3 className="font-display text-2xl">Corrections</h3>
                  <p className="text-base text-muted">They stay on this phone. Nothing is sent off to retrain.</p>
                  <div className="mt-2 flex gap-2">
                    <input
                      value={correction}
                      onChange={(event) => setCorrection(event.target.value)}
                      placeholder={primary ? `${primary.klass} is actually…` : "Correct name"}
                      aria-label="Correction"
                      className="min-h-12 min-w-0 flex-1 rounded-2xl border border-line bg-surface px-3 text-base"
                    />
                    <button
                      type="button"
                      className="min-h-12 rounded-2xl bg-surface px-4 text-base"
                      onClick={() => {
                        if (!primary || !correction.trim()) return;
                        settings.addCorrection({
                          id: `${Date.now()}`,
                          at: Date.now(),
                          predicted: primary.klass,
                          corrected: correction.trim(),
                        });
                        setCorrection("");
                      }}
                    >
                      Log
                    </button>
                  </div>
                  <ul className="mt-2 text-base">
                    {settings.corrections.map((item) => (
                      <li key={item.id}>
                        <span className="text-muted">{item.predicted}</span> → {item.corrected}
                      </li>
                    ))}
                  </ul>
                </div>
                <button
                  type="button"
                  className="inline-flex min-h-12 items-center gap-2 self-start text-base"
                  onClick={() => report && speak(report.phrase, settings.language)}
                >
                  {settings.voice ? <Volume2 className="size-5" /> : <VolumeX className="size-5" />}
                  Speak this line
                </button>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

type SpeechRec = {
  lang: string;
  interimResults: boolean;
  maxAlternatives: number;
  start: () => void;
  onresult: ((event: { results: Array<Array<{ transcript: string }>> }) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
};

type InstallPrompt = Event & { prompt: () => Promise<void> };

function rangeWord(method: string | undefined) {
  if (method === "TruePath") return "ground";
  if (method === "SkyShield") return "overhead";
  if (!method) return "quiet";
  return method.toLowerCase();
}

function NavButton({
  active,
  label,
  onClick,
  children,
}: {
  active: boolean;
  label: string;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex min-h-14 flex-col items-center justify-center gap-0.5 rounded-2xl text-sm font-bold ${active ? "bg-fg text-bg" : "bg-surface text-muted"}`}
    >
      {children}
      {label}
    </button>
  );
}

function Toggle({ on, label, pressed }: { on: boolean; label: string; pressed: () => void }) {
  return (
    <button
      type="button"
      onClick={pressed}
      className={`min-h-12 rounded-2xl text-base font-bold ${on ? "bg-fg text-bg" : "bg-surface text-muted"}`}
    >
      {label}
    </button>
  );
}

function Slider({
  label,
  value,
  min,
  max,
  step,
  unit,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  unit: string;
  onChange: (value: number) => void;
}) {
  const id = label.replace(/\s+/g, "-").toLowerCase();
  return (
    <label htmlFor={id} className="block text-base">
      <span>{label}</span>
      <span className="float-right font-bold text-cyan">
        {value.toFixed(step < 1 ? 2 : 0)}
        {unit}
      </span>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
        className="mt-2 w-full accent-cyan"
      />
    </label>
  );
}
