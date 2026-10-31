import { create } from "zustand";
import { persist } from "zustand/middleware";

export type PlaceMemory = {
  id: string;
  name: string;
  at: number;
  note: string;
};

export type Correction = {
  id: string;
  at: number;
  predicted: string;
  corrected: string;
};

export type TargetItem = {
  id: string;
  name: string;
  note: string;
  lastSeenAt: number | null;
  lastWhere: string | null;
};

type Settings = {
  cameraHeight: number;
  fov: number;
  pitch: number;
  scale: number;
  voice: boolean;
  haptics: boolean;
  sonar: boolean;
  language: string;
  places: PlaceMemory[];
  corrections: Correction[];
  targets: TargetItem[];
  patch: (
    partial: Partial<
      Pick<Settings, "cameraHeight" | "fov" | "pitch" | "scale" | "voice" | "haptics" | "sonar" | "language">
    >,
  ) => void;
  addPlace: (place: PlaceMemory) => void;
  removePlace: (id: string) => void;
  addCorrection: (correction: Correction) => void;
  addTarget: (name: string, note: string) => void;
  removeTarget: (id: string) => void;
  markSeen: (name: string, where: string) => void;
};

const seedTargets = (names: string[]): TargetItem[] =>
  names.map((name) => ({
    id: name,
    name,
    note: `Lock onto a ${name}`,
    lastSeenAt: null,
    lastWhere: null,
  }));

export const useAuriga = create<Settings>()(
  persist(
    (set) => ({
      cameraHeight: 1.4,
      fov: 66,
      pitch: 14,
      scale: 1,
      voice: true,
      haptics: true,
      sonar: false,
      language: "en-US",
      places: [],
      corrections: [],
      targets: seedTargets(["person", "chair", "cup", "car"]),
      patch: (partial) => set(partial),
      addPlace: (place) => set((s) => ({ places: [place, ...s.places].slice(0, 24) })),
      removePlace: (id) => set((s) => ({ places: s.places.filter((p) => p.id !== id) })),
      addCorrection: (correction) =>
        set((s) => ({ corrections: [correction, ...s.corrections].slice(0, 40) })),
      addTarget: (name, note) =>
        set((s) => {
          if (s.targets.some((t) => t.name === name)) return s;
          return {
            targets: [
              {
                id: `${name}-${Date.now()}`,
                name,
                note: note || `Find a ${name}`,
                lastSeenAt: null,
                lastWhere: null,
              },
              ...s.targets,
            ].slice(0, 24),
          };
        }),
      removeTarget: (id) => set((s) => ({ targets: s.targets.filter((t) => t.id !== id) })),
      markSeen: (name, where) =>
        set((s) => {
          const current = s.targets.find((t) => t.name === name);
          if (!current) return s;
          if (current.lastWhere === where && current.lastSeenAt && Date.now() - current.lastSeenAt < 8000) return s;
          return {
            targets: s.targets.map((t) =>
              t.name === name ? { ...t, lastSeenAt: Date.now(), lastWhere: where } : t,
            ),
          };
        }),
    }),
    { name: "auriga-pwa" },
  ),
);
