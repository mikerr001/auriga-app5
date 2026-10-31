import {
  horizonRow,
  projectPoint,
  rowForGroundDistance,
  type FrameReport,
  type HazardLevel,
  type MapPoint,
  type Pose,
  type Rig,
} from "@/lib/auriga/engine";

export type Ink = {
  bg: string;
  fg: string;
  muted: string;
  line: string;
  accent: string;
  alert: string;
};

export function readInk(node: HTMLElement): Ink {
  const style = getComputedStyle(node);
  const pick = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
  return {
    bg: pick("--color-bg", "#070b10"),
    fg: pick("--color-fg", "#e8f7f6"),
    muted: pick("--color-muted", "#8ea3ae"),
    line: pick("--color-line", "#24323d"),
    accent: pick("--color-cyan", "#3ee0d2"),
    alert: pick("--color-alert", "#ff6b7a"),
  };
}

function tone(level: HazardLevel | undefined, ink: Ink) {
  if (level === "critical") return ink.alert;
  if (level === "caution") return ink.accent;
  return ink.muted;
}

export function paintGuide(
  ctx: CanvasRenderingContext2D,
  rig: Rig,
  pose: Pose,
  report: FrameReport | null,
  video: HTMLVideoElement | null,
  ink: Ink,
) {
  const { frameW: w, frameH: h } = rig;
  ctx.clearRect(0, 0, w, h);
  if (video && video.readyState >= 2 && video.videoWidth > 0) {
    ctx.drawImage(video, 0, 0, w, h);
    ctx.fillStyle = ink.bg;
    ctx.globalAlpha = 0.22;
    ctx.fillRect(0, 0, w, h);
    ctx.globalAlpha = 1;
  } else {
    const sky = ctx.createLinearGradient(0, 0, 0, h);
    sky.addColorStop(0, "#efe2c8");
    sky.addColorStop(0.45, ink.bg);
    sky.addColorStop(1, "#e7d3b0");
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, w, h);
    drawGrid(ctx, rig, pose, ink);
  }

  const horizon = horizonRow(rig);
  ctx.strokeStyle = ink.line;
  ctx.setLineDash([5, 9]);
  ctx.beginPath();
  ctx.moveTo(0, horizon);
  ctx.lineTo(w, horizon);
  ctx.stroke();
  ctx.setLineDash([]);

  ctx.strokeStyle = ink.line;
  ctx.globalAlpha = 0.45;
  for (const x of [w / 3, (2 * w) / 3]) {
    ctx.beginPath();
    ctx.moveTo(x, horizon);
    ctx.lineTo(x, h);
    ctx.stroke();
  }
  ctx.globalAlpha = 1;

  const primary = report?.primary;
  if (primary?.worldX != null && primary.worldZ != null) {
    drawFiducial(ctx, rig, pose, primary.worldX, primary.worldZ, ink);
    if (primary.suspended && primary.distanceM) {
      const nadir = rowForGroundDistance(primary.distanceM, rig);
      if (nadir != null) {
        const cx = primary.x + primary.w / 2;
        ctx.strokeStyle = ink.accent;
        ctx.setLineDash([3, 5]);
        ctx.beginPath();
        ctx.moveTo(cx, primary.y + primary.h);
        ctx.lineTo(cx, nadir);
        ctx.stroke();
        ctx.setLineDash([]);
      }
    }
  }

  for (const reading of report?.readings ?? []) {
    const level = report?.hazards.find((item) => item.id === reading.id)?.level;
    const color = tone(level, ink);
    bracket(ctx, reading.x, reading.y, reading.w, reading.h, color, level === "critical" ? 3 : 2);
    const label = `${reading.klass}  ${reading.distanceM == null ? "—" : reading.distanceM.toFixed(1) + " m"}`;
    ctx.font = "700 22px Atkinson Hyperlegible, sans-serif";
    const pad = 8;
    const tw = ctx.measureText(label).width + pad * 2;
    const ty = Math.max(8, reading.y - 30);
    ctx.fillStyle = ink.bg;
    ctx.globalAlpha = 0.86;
    ctx.fillRect(reading.x, ty, tw, 26);
    ctx.globalAlpha = 1;
    ctx.fillStyle = color;
    ctx.fillText(label, reading.x + pad, ty + 18);
  }

  ctx.strokeStyle = ink.accent;
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.arc(w / 2, h / 2, 18, 0, Math.PI * 2);
  ctx.moveTo(w / 2 - 28, h / 2);
  ctx.lineTo(w / 2 - 8, h / 2);
  ctx.moveTo(w / 2 + 8, h / 2);
  ctx.lineTo(w / 2 + 28, h / 2);
  ctx.moveTo(w / 2, h / 2 - 28);
  ctx.lineTo(w / 2, h / 2 - 8);
  ctx.moveTo(w / 2, h / 2 + 8);
  ctx.lineTo(w / 2, h / 2 + 28);
  ctx.stroke();
  ctx.fillStyle = ink.accent;
  ctx.beginPath();
  ctx.arc(w / 2, h / 2, 2.5, 0, Math.PI * 2);
  ctx.fill();
  ctx.lineWidth = 1;
}

function bracket(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  color: string,
  width: number,
) {
  const a = Math.max(10, Math.min(w, h) * 0.22);
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.beginPath();
  ctx.moveTo(x, y + a);
  ctx.lineTo(x, y);
  ctx.lineTo(x + a, y);
  ctx.moveTo(x + w - a, y);
  ctx.lineTo(x + w, y);
  ctx.lineTo(x + w, y + a);
  ctx.moveTo(x + w, y + h - a);
  ctx.lineTo(x + w, y + h);
  ctx.lineTo(x + w - a, y + h);
  ctx.moveTo(x + a, y + h);
  ctx.lineTo(x, y + h);
  ctx.lineTo(x, y + h - a);
  ctx.stroke();
  ctx.lineWidth = 1;
}

function drawGrid(ctx: CanvasRenderingContext2D, rig: Rig, pose: Pose, ink: Ink) {
  ctx.strokeStyle = ink.line;
  ctx.lineWidth = 1;
  const originX = Math.round(pose.x);
  const originZ = Math.round(pose.z);
  for (let z = originZ; z <= originZ + 14; z += 1) {
    const a = projectPoint(-6, 0, z, pose, rig);
    const b = projectPoint(6, 0, z, pose, rig);
    if (!a || !b) continue;
    ctx.beginPath();
    ctx.moveTo(a.u, a.v);
    ctx.lineTo(b.u, b.v);
    ctx.stroke();
  }
  for (let x = -6; x <= 6; x += 1) {
    const a = projectPoint(x, 0, originZ + 0.6, pose, rig);
    const b = projectPoint(x, 0, originZ + 14, pose, rig);
    if (!a || !b) continue;
    ctx.beginPath();
    ctx.moveTo(a.u, a.v);
    ctx.lineTo(b.u, b.v);
    ctx.stroke();
  }
}

function drawFiducial(
  ctx: CanvasRenderingContext2D,
  rig: Rig,
  pose: Pose,
  x: number,
  z: number,
  ink: Ink,
) {
  const s = 0.1;
  const corners = [
    projectPoint(x - s, 0, z - s, pose, rig),
    projectPoint(x + s, 0, z - s, pose, rig),
    projectPoint(x + s, 0, z + s, pose, rig),
    projectPoint(x - s, 0, z + s, pose, rig),
  ];
  if (corners.some((c) => !c)) return;
  ctx.beginPath();
  corners.forEach((c, i) => {
    if (!c) return;
    if (i === 0) ctx.moveTo(c.u, c.v);
    else ctx.lineTo(c.u, c.v);
  });
  ctx.closePath();
  ctx.fillStyle = ink.accent;
  ctx.globalAlpha = 0.28;
  ctx.fill();
  ctx.globalAlpha = 1;
  ctx.strokeStyle = ink.accent;
  ctx.stroke();
}

export function paintMap(
  ctx: CanvasRenderingContext2D,
  pose: Pose,
  cloud: MapPoint[],
  anchor: { x: number; z: number } | null,
  ink: Ink,
) {
  const w = ctx.canvas.width;
  const h = ctx.canvas.height;
  ctx.clearRect(0, 0, w, h);
  ctx.fillStyle = ink.bg;
  ctx.fillRect(0, 0, w, h);

  ctx.strokeStyle = ink.line;
  ctx.lineWidth = 1;
  const cx = w / 2;
  const cy = h * 0.72;
  for (const radius of [70, 140, 210, 280]) {
    ctx.beginPath();
    ctx.arc(cx, cy, radius, 0, Math.PI * 2);
    ctx.stroke();
  }
  ctx.beginPath();
  ctx.moveTo(cx, 24);
  ctx.lineTo(cx, h - 24);
  ctx.moveTo(24, cy);
  ctx.lineTo(w - 24, cy);
  ctx.stroke();

  for (const point of cloud) {
    const p = mapPoint(point.x, point.z, pose, w, h);
    if (p.sx < -20 || p.sy < -20 || p.sx > w + 20 || p.sy > h + 20) continue;
    ctx.fillStyle = point.kind === "ground" ? ink.line : tone(point.level, ink);
    ctx.beginPath();
    ctx.arc(p.sx, p.sy, point.kind === "ground" ? 1.6 : 4.5, 0, Math.PI * 2);
    ctx.fill();
  }
  if (anchor) {
    const p = mapPoint(anchor.x, anchor.z, pose, w, h);
    ctx.strokeStyle = ink.accent;
    ctx.lineWidth = 2;
    ctx.strokeRect(p.sx - 8, p.sy - 8, 16, 16);
    ctx.lineWidth = 1;
  }
  const c = mapPoint(pose.x, pose.z, pose, w, h);
  ctx.fillStyle = ink.accent;
  ctx.beginPath();
  ctx.moveTo(c.sx, c.sy - 14);
  ctx.lineTo(c.sx + 8, c.sy + 9);
  ctx.lineTo(c.sx - 8, c.sy + 9);
  ctx.closePath();
  ctx.fill();
}

function mapPoint(x: number, z: number, pose: Pose, w: number, h: number) {
  const dx = x - pose.x;
  const dz = z - pose.z;
  const head = (pose.heading * Math.PI) / 180;
  const xCam = dx * Math.cos(head) - dz * Math.sin(head);
  const zCam = dx * Math.sin(head) + dz * Math.cos(head);
  return { sx: w / 2 + xCam * 26, sy: h * 0.72 - zCam * 26 };
}
