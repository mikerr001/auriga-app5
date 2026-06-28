import { pgTable, serial, integer, text, real, boolean, timestamp } from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";
import { devicesTable } from "./devices";

export const hazardsTable = pgTable("hazards", {
  id: serial("id").primaryKey(),
  deviceId: integer("device_id").notNull().references(() => devicesTable.id),
  hazardType: text("hazard_type").notNull(),
  confidence: real("confidence").notNull(),
  distanceMeters: real("distance_meters"),
  bearingDegrees: real("bearing_degrees"),
  audioAlertFired: boolean("audio_alert_fired"),
  hapticAlertFired: boolean("haptic_alert_fired"),
  notes: text("notes"),
  detectedAt: timestamp("detected_at", { withTimezone: true }).notNull().defaultNow(),
});

export const insertHazardSchema = createInsertSchema(hazardsTable).omit({ id: true, detectedAt: true });
export type InsertHazard = z.infer<typeof insertHazardSchema>;
export type Hazard = typeof hazardsTable.$inferSelect;
