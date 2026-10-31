/** Fused Auriga spatial kernel.
 * TruePath + SkyShield from the Java triangulation engine,
 * pinhole Virtual Fiducials and hazard scoring from the co-pilot repos,
 * and a ground-ray stream in the spirit of LingBot-Map's incremental fusion.
 * Geometry only — no neural weights.
 */

export type Pose = { x: number; z: number; heading: number };

export type Rig = {
  frameW: number;
  frameH: number;
  fov: number;
  pitchDownDeg: number;
  cameraHeight: number;
  scale: number;
};

export type Detection = {
  id: string;
  klass: string;
  score: number;
  x: number;
  y: number;
  w: number;
  h: number;
};

export type WorldObject = {
  id: string;
  klass: string;
  x: number;
  z: number;
  widthM: number;
  heightM: number;
  baseM: number;
  vx: number;
  vz: number;
};

export type SpatialReading = Detection & {
  distanceM: number | null;
  heightM: number;
  bearingDeg: number;
  column: "left" | "center" | "right";
  signature: "WALL" | "POLE" | "OBJECT" | "OVERHANG";
  sanity: number;
  method: string;
  suspended: boolean;
  worldX: number | null;
  worldZ: number | null;
};

export type HazardLevel = "watch" | "caution" | "critical";

export type Hazard = SpatialReading & {
  severity: number;
  probability: number;
  risk: number;
  ttc: number | null;
  level: HazardLevel;
};

export type FrameReport = {
  readings: SpatialReading[];
  hazards: Hazard[];
  primary: Hazard | null;
  phrase: string;
  columns: { left: string; center: string; right: string };
};

export type Track = { t: number; dist: number };

export type MapPoint = {
  x: number;
  z: number;
  kind: "object" | "ground";
  klass?: string;
  level?: HazardLevel;
};

export const REFERENCE_WIDTHS: Record<string, number> = {
  person: 0.45,
  bicycle: 0.6,
  car: 1.8,
  motorcycle: 0.8,
  bus: 2.5,
  truck: 2.2,
  bottle: 0.07,
  cup: 0.08,
  chair: 0.5,
  couch: 1.8,
  dog: 0.3,
  cat: 0.25,
  door: 0.9,
  stairs: 1.2,
  pole: 0.12,
  "hanging sign": 0.8,
  "stop sign": 0.75,
  "traffic light": 0.25,
  backpack: 0.32,
  suitcase: 0.45,
  bench: 1.4,
};

const SEVERITY: Record<string, number> = {
  car: 5,
  truck: 5,
  bus: 5,
  motorcycle: 4,
  bicycle: 4,
  person: 3,
  stairs: 5,
  "hanging sign": 3,
  curb: 4,
  dog: 3,
  pole: 3,
  door: 1,
  chair: 2,
  couch: 2,
  cup: 1,
  bottle: 1,
};

export const LANGUAGES: [string, string][] = [
  ["en-US", "English"],
  ["sw-KE", "Kiswahili"],
  ["am-ET", "Amharic"],
  ["ha-NG", "Hausa"],
  ["yo-NG", "Yoruba"],
  ["ig-NG", "Igbo"],
  ["zu-ZA", "isiZulu"],
  ["xh-ZA", "isiXhosa"],
  ["rw-RW", "Kinyarwanda"],
  ["so-SO", "Somali"],
  ["ar-SA", "Arabic"],
  ["fr-FR", "French"],
  ["pt-BR", "Portuguese"],
  ["hi-IN", "Hindi"],
  ["bn-BD", "Bengali"],
  ["ur-PK", "Urdu"],
  ["zh-CN", "Chinese"],
  ["id-ID", "Indonesian"],
  ["vi-VN", "Vietnamese"],
  ["ja-JP", "Japanese"],
  ["ko-KR", "Korean"],
  ["es-ES", "Spanish"],
  ["de-DE", "German"],
  ["tr-TR", "Turkish"],
  ["ru-RU", "Russian"],
];

export function seedWorld(): WorldObject[] {
  return [
    { id: "chair", klass: "chair", x: -0.65, z: 2.5, widthM: 0.5, heightM: 0.9, baseM: 0, vx: 0, vz: 0 },
    { id: "cup", klass: "cup", x: 0.42, z: 1.7, widthM: 0.08, heightM: 0.1, baseM: 0.75, vx: 0, vz: 0 },
    { id: "sign", klass: "hanging sign", x: 0.15, z: 4.4, widthM: 0.8, heightM: 0.4, baseM: 2.2, vx: 0, vz: 0 },
    { id: "pole", klass: "pole", x: -1.7, z: 6.2, widthM: 0.12, heightM: 2.8, baseM: 0, vx: 0, vz: 0 },
    { id: "person", klass: "person", x: 1.6, z: 5.8, widthM: 0.45, heightM: 1.7, baseM: 0, vx: -0.42, vz: 0 },
    { id: "bicycle", klass: "bicycle", x: -2.4, z: 8.2, widthM: 0.6, heightM: 1.1, baseM: 0, vx: 0.9, vz: -0.15 },
    { id: "stairs", klass: "stairs", x: -0.2, z: 9.4, widthM: 1.2, heightM: 0.35, baseM: 0, vx: 0, vz: 0 },
    { id: "car", klass: "car", x: 1.15, z: 16, widthM: 1.8, heightM: 1.5, baseM: 0, vx: 0, vz: -2.4 },
    { id: "door", klass: "door", x: 0.05, z: 12.5, widthM: 0.9, heightM: 2.1, baseM: 0, vx: 0, vz: 0 },
  ];
}

export function defaultRig(partial?: Partial<Rig>): Rig {
  return {
    frameW: 960,
    frameH: 540,
    fov: 66,
    pitchDownDeg: 14,
    cameraHeight: 1.4,
    scale: 1,
    ...partial,
  };
}

export function focalPx(frameW: number, fovDeg: number) {
  const half = (fovDeg * Math.PI) / 360;
  return frameW / (2 * Math.tan(half));
}

export function normalizeHeading(deg: number) {
  return ((deg % 360) + 360) % 360;
}

export function movePose(pose: Pose, distance: number): Pose {
  const h = (pose.heading * Math.PI) / 180;
  return {
    heading: pose.heading,
    x: pose.x + Math.sin(h) * distance,
    z: pose.z + Math.cos(h) * distance,
  };
}

export function clampPose(pose: Pose): Pose {
  return {
    heading: normalizeHeading(pose.heading),
    x: Math.max(-6, Math.min(6, pose.x)),
    z: Math.max(-1, Math.min(14, pose.z)),
  };
}

type CamPoint = { u: number; v: number; depth: number };

export function projectPoint(
  wx: number,
  wy: number,
  wz: number,
  pose: Pose,
  rig: Rig,
): CamPoint | null {
  const dx = wx - pose.x;
  const dz = wz - pose.z;
  const h = (pose.heading * Math.PI) / 180;
  const xCam = dx * Math.cos(h) - dz * Math.sin(h);
  const zForward = dx * Math.sin(h) + dz * Math.cos(h);
  const yCam = wy - rig.cameraHeight;
  const pitch = (rig.pitchDownDeg * Math.PI) / 180;
  const y = yCam * Math.cos(pitch) + zForward * Math.sin(pitch);
  const z = -yCam * Math.sin(pitch) + zForward * Math.cos(pitch);
  if (z < 0.35) return null;
  const f = focalPx(rig.frameW, rig.fov);
  return {
    u: rig.frameW / 2 + (f * xCam) / z,
    v: rig.frameH / 2 - (f * y) / z,
    depth: z,
  };
}

export function advanceWorld(objects: WorldObject[], dt: number): WorldObject[] {
  return objects.map((o) => {
    let x = o.x + o.vx * dt;
    let z = o.z + o.vz * dt;
    let vx = o.vx;
    if (o.id === "car" && z < 1.4) z = 18;
    if (o.id === "person" && (x < -2.2 || x > 2.5)) vx = -vx;
    if (o.id === "bicycle" && (x < -3.3 || x > 3.3)) vx = -vx;
    x = o.id === "person" || o.id === "bicycle" ? Math.max(-3.3, Math.min(3.3, x)) : x;
    return { ...o, x, z, vx };
  });
}

export function projectWorld(objects: WorldObject[], pose: Pose, rig: Rig): Detection[] {
  const detections: Detection[] = [];
  for (const o of objects) {
    const half = o.widthM / 2;
    const corners = [
      projectPoint(o.x - half, o.baseM, o.z, pose, rig),
      projectPoint(o.x + half, o.baseM, o.z, pose, rig),
      projectPoint(o.x - half, o.baseM + o.heightM, o.z, pose, rig),
      projectPoint(o.x + half, o.baseM + o.heightM, o.z, pose, rig),
    ].filter((p): p is CamPoint => p !== null);
    if (corners.length < 4) continue;
    const us = corners.map((c) => c.u);
    const vs = corners.map((c) => c.v);
    const x = Math.min(...us);
    const y = Math.min(...vs);
    const w = Math.max(...us) - x;
    const h = Math.max(...vs) - y;
    if (w < 2 || h < 2) continue;
    const cx = x + w / 2;
    const cy = y + h / 2;
    const inside =
      cx > -rig.frameW * 0.15 &&
      cx < rig.frameW * 1.15 &&
      cy > -rig.frameH * 0.2 &&
      cy < rig.frameH * 1.2;
    if (!inside) continue;
    const clipped =
      x < 0 || y < 0 || x + w > rig.frameW || y + h > rig.frameH;
    detections.push({
      id: o.id,
      klass: o.klass,
      score: clipped ? 0.72 : 0.9,
      x,
      y,
      w,
      h,
    });
  }
  return detections;
}

export function groundDistanceFromRow(row: number, rig: Rig): number | null {
  const f = focalPx(rig.frameW, rig.fov);
  const pitch = (rig.pitchDownDeg * Math.PI) / 180;
  const alpha = pitch + Math.atan((row - rig.frameH / 2) / f);
  if (alpha <= 0.035 || alpha >= 1.25) return null;
  const d = rig.cameraHeight / Math.tan(alpha);
  if (!Number.isFinite(d) || d < 0.25 || d > 40) return null;
  return d;
}

export function rowForGroundDistance(dist: number, rig: Rig): number | null {
  if (dist < 0.25) return null;
  const f = focalPx(rig.frameW, rig.fov);
  const pitch = (rig.pitchDownDeg * Math.PI) / 180;
  const delta = Math.atan(rig.cameraHeight / dist) - pitch;
  return rig.frameH / 2 + f * Math.tan(delta);
}

export function horizonRow(rig: Rig) {
  const f = focalPx(rig.frameW, rig.fov);
  const pitch = (rig.pitchDownDeg * Math.PI) / 180;
  return rig.frameH / 2 - f * Math.tan(pitch);
}

function widthDistance(bboxW: number, refWidth: number, rig: Rig): number | null {
  if (bboxW < 2) return null;
  const f = focalPx(rig.frameW, rig.fov);
  const d = (refWidth * rig.scale * f) / bboxW;
  if (!Number.isFinite(d) || d < 0.15 || d > 80) return null;
  return d;
}

function heightAboveGround(baseY: number, horizontal: number, rig: Rig) {
  const f = focalPx(rig.frameW, rig.fov);
  const pitch = (rig.pitchDownDeg * Math.PI) / 180;
  const gamma = Math.atan((rig.frameH / 2 - baseY) / f) - pitch;
  const height = rig.cameraHeight + horizontal * Math.tan(gamma);
  if (!Number.isFinite(height)) return 0;
  return Math.max(0, Math.min(8, height));
}

function columnOf(centerX: number, frameW: number): SpatialReading["column"] {
  const t = centerX / frameW;
  if (t < 0.33) return "left";
  if (t > 0.67) return "right";
  return "center";
}

export function worldFromReading(pose: Pose, distance: number, bearingDeg: number) {
  const bearing = (bearingDeg * Math.PI) / 180;
  const heading = (pose.heading * Math.PI) / 180;
  const xRel = distance * Math.sin(bearing);
  const zRel = distance * Math.cos(bearing);
  return {
    x: pose.x + xRel * Math.cos(heading) + zRel * Math.sin(heading),
    z: pose.z - xRel * Math.sin(heading) + zRel * Math.cos(heading),
  };
}

export function perceive(det: Detection, rig: Rig, pose: Pose): SpatialReading | null {
  const f = focalPx(rig.frameW, rig.fov);
  const ref = REFERENCE_WIDTHS[det.klass] ?? 0.4;
  const centerX = det.x + det.w / 2;
  const bottom = det.y + det.h;
  const dWidth = widthDistance(det.w, ref, rig);
  const dRow = groundDistanceFromRow(bottom, rig);
  const aboveHorizon = bottom < horizonRow(rig) - 4;
  const aspect = det.h / Math.max(det.w, 1);

  let distance: number | null = null;
  let sanity = 1;
  let method = "none";
  let suspended = false;
  let height = 0;

  const elevate = (horizontal: number) => {
    const guess = heightAboveGround(bottom, horizontal, rig);
    return aspect < 3.2 && guess > 0.45;
  };

  if (aboveHorizon && dWidth) {
    suspended = true;
    distance = dWidth;
    method = "SkyShield";
    height = heightAboveGround(bottom, dWidth, rig);
  } else if (dRow && dWidth && elevate(dWidth) && dWidth < dRow * 0.82) {
    suspended = true;
    distance = dWidth;
    method = "SkyShield";
    height = heightAboveGround(bottom, dWidth, rig);
    sanity = 0.86;
  } else if (dRow && dWidth) {
    const rel = Math.abs(dWidth - dRow) / Math.max(dRow, 0.01);
    sanity = rel > 0.25 ? Math.max(0.15, 1 - (rel - 0.25) / 0.75) : 1;
    distance = dRow;
    method = "TruePath";
    height = 0;
  } else if (dRow) {
    distance = dRow;
    method = "TruePath";
  } else if (dWidth) {
    distance = dWidth;
    method = "fiducial width";
    if (elevate(dWidth)) {
      suspended = true;
      method = "SkyShield";
      height = heightAboveGround(bottom, dWidth, rig);
    }
  }

  if (distance == null) return null;
  const bearingDeg = (Math.atan((centerX - rig.frameW / 2) / f) * 180) / Math.PI;
  const world = worldFromReading(pose, distance, bearingDeg);
  const widthEst = (distance * det.w) / f;
  const wide = new Set(["car", "bus", "truck", "couch"]);
  let signature: SpatialReading["signature"] = "OBJECT";
  if (suspended) signature = "OVERHANG";
  else if (widthEst < 0.28) signature = "POLE";
  else if (widthEst > 1.5 && !wide.has(det.klass)) signature = "WALL";
  return {
    ...det,
    distanceM: distance,
    heightM: suspended ? height : height > 0.35 ? height : 0,
    bearingDeg,
    column: columnOf(centerX, rig.frameW),
    signature: suspended ? "OVERHANG" : signature,
    sanity,
    method,
    suspended,
    worldX: world.x,
    worldZ: world.z,
  };
}

function distanceMultiplier(distance: number | null) {
  if (distance == null) return 1;
  if (distance < 1.5) return 1.85;
  if (distance < 3) return 1.35;
  if (distance < 6) return 1;
  return 0.62;
}

function levelFor(risk: number): HazardLevel {
  if (risk >= 4.4) return "critical";
  if (risk >= 2.15) return "caution";
  return "watch";
}

export function analyze(
  dets: Detection[],
  rig: Rig,
  pose: Pose,
  tracks: Map<string, Track>,
  now: number,
): { report: FrameReport; tracks: Map<string, Track> } {
  const next = new Map(tracks);
  const readings = dets
    .map((d) => perceive(d, rig, pose))
    .filter((r): r is SpatialReading => r !== null);

  const hazards: Hazard[] = readings.map((reading) => {
    const severity = SEVERITY[reading.klass] ?? 2;
    const prev = tracks.get(reading.id);
    let ttc: number | null = null;
    if (reading.distanceM != null) {
      if (prev && now > prev.t) {
        const dt = (now - prev.t) / 1000;
        const closing = dt > 0.05 ? (prev.dist - reading.distanceM) / dt : 0;
        if (closing > 0.2) ttc = reading.distanceM / closing;
      }
      next.set(reading.id, { t: now, dist: reading.distanceM });
    }
    const absB = Math.abs(reading.bearingDeg);
    const forward = absB < 14 ? 1 : absB < 32 ? 0.72 : absB < 55 ? 0.42 : 0.22;
    const closingProb = ttc != null && ttc > 0 && ttc < 8 ? Math.min(1, 1.6 / ttc) : 0.4;
    const probability = Math.min(1, forward * (0.42 + 0.58 * closingProb));
    const confidence = Math.max(0.05, Math.min(1, reading.score * reading.sanity));
    const risk = severity * probability * confidence * distanceMultiplier(reading.distanceM);
    return { ...reading, severity, probability, risk, ttc, level: levelFor(risk) };
  });

  hazards.sort((a, b) => b.risk - a.risk);
  const byDistance = (a: Hazard, b: Hazard) => (a.distanceM ?? 99) - (b.distanceM ?? 99);
  const hot = hazards.filter((item) => item.level !== "watch");
  const centered = hazards.filter((item) => item.column === "center").sort(byDistance);
  const primary = hot[0] ?? centered[0] ?? hazards.slice().sort(byDistance)[0] ?? null;
  const phrase = speakPhrase(primary, hazards);
  return {
    tracks: next,
    report: {
      readings,
      hazards,
      primary,
      phrase,
      columns: {
        left: columnPhrase(hazards, "left"),
        center: columnPhrase(hazards, "center"),
        right: columnPhrase(hazards, "right"),
      },
    },
  };
}

export function formatMeters(distance: number | null) {
  if (distance == null || !Number.isFinite(distance)) return "unknown distance";
  if (distance < 1) return "under a meter";
  if (distance < 10) return `${distance.toFixed(1)} meters`;
  return `${Math.round(distance)} meters`;
}

export function bearingPhrase(bearing: number) {
  const abs = Math.abs(Math.round(bearing));
  if (abs < 7) return "straight ahead";
  return `${abs} degrees ${bearing < 0 ? "left" : "right"}`;
}

function columnPhrase(hazards: Hazard[], column: Hazard["column"]) {
  const items = hazards.filter((h) => h.column === column).sort((a, b) => (a.distanceM ?? 99) - (b.distanceM ?? 99));
  const nearest = items[0];
  if (!nearest) return "clear";
  return `${nearest.klass}, ${formatMeters(nearest.distanceM)}`;
}

export function speakPhrase(primary: Hazard | null, hazards: Hazard[]) {
  if (!primary) return "Nothing in view. Turn slowly and I will read the yard.";
  const where = `${primary.klass}, ${bearingPhrase(primary.bearingDeg)}, ${formatMeters(primary.distanceM)}`;
  const closing =
    primary.ttc != null && primary.ttc < 6 ? ` Closing in about ${Math.max(1, Math.round(primary.ttc))} seconds.` : "";
  const overhead =
    primary.suspended && primary.heightM > 0.4
      ? ` Base is ${primary.heightM.toFixed(1)} meters off the ground.`
      : "";
  if (primary.level === "critical") return `Stop. ${where}.${closing}${overhead}`;
  if (primary.level === "caution") return `Caution. ${where}.${closing}${overhead}`;
  return `${where}.${overhead}`;
}

export function answerQuestion(question: string, report: FrameReport | null) {
  if (!report) return "I do not have a frame yet.";
  const q = question.toLowerCase();
  const { columns, hazards, primary } = report;
  if (!hazards.length) return "I cannot see a labeled object right now.";
  if (/(left)/.test(q)) return `On your left: ${columns.left}.`;
  if (/(right)/.test(q)) return `On your right: ${columns.right}.`;
  if (/(ahead|front|center|straight)/.test(q)) return `Ahead: ${columns.center}.`;
  if (/(safe|danger|hazard|clear)/.test(q)) {
    const hot = hazards.filter((h) => h.level !== "watch");
    if (!hot.length) return `No caution-level hazard. Nearest is ${primary ? speakPhrase(primary, hazards) : "unknown"}.`;
    return hot
      .slice(0, 2)
      .map((h) => speakPhrase(h, hazards))
      .join(" ");
  }
  if (/(height|overhead|head|sign)/.test(q)) {
    const over = hazards.find((h) => h.suspended || h.signature === "OVERHANG");
    if (!over) return "No overhang in this frame.";
    return `${over.klass} base is ${over.heightM.toFixed(1)} meters above the ground, ${formatMeters(over.distanceM)}, ${bearingPhrase(over.bearingDeg)}.`;
  }
  const named = hazards.find((h) => q.includes(h.klass));
  if (named) return speakPhrase(named, hazards);
  return speakPhrase(primary, hazards);
}

export function groundSamples(pose: Pose, rig: Rig): MapPoint[] {
  const points: MapPoint[] = [];
  for (let i = 0; i < 7; i++) {
    const u = ((i + 0.5) / 7) * rig.frameW;
    const row = rig.frameH * 0.86;
    const dist = groundDistanceFromRow(row, rig);
    if (!dist) continue;
    const f = focalPx(rig.frameW, rig.fov);
    const bearing = (Math.atan((u - rig.frameW / 2) / f) * 180) / Math.PI;
    const world = worldFromReading(pose, dist, bearing);
    points.push({ x: world.x, z: world.z, kind: "ground" });
  }
  return points;
}

export function quantizeCloud(existing: MapPoint[], incoming: MapPoint[], cap = 480): MapPoint[] {
  const key = (p: MapPoint) =>
    `${p.kind}:${p.klass ?? ""}:${Math.round(p.x * 2.5)}:${Math.round(p.z * 2.5)}`;
  const map = new Map<string, MapPoint>();
  for (const p of existing) map.set(key(p), p);
  for (const p of incoming) map.set(key(p), p);
  const all = [...map.values()];
  return all.slice(Math.max(0, all.length - cap));
}
