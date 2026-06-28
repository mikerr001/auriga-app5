import { pgTable, serial, integer, text, boolean, timestamp } from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";
import { devicesTable } from "./devices";

export const sessionsTable = pgTable("sessions", {
  id: serial("id").primaryKey(),
  deviceId: integer("device_id").notNull().references(() => devicesTable.id),
  locationName: text("location_name").notNull(),
  durationMinutes: integer("duration_minutes").notNull(),
  hazardsDetected: integer("hazards_detected"),
  modelLoaded: boolean("model_loaded"),
  cameraWorked: boolean("camera_worked"),
  audioWorked: boolean("audio_worked"),
  notes: text("notes"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const insertSessionSchema = createInsertSchema(sessionsTable).omit({ id: true, createdAt: true });
export type InsertSession = z.infer<typeof insertSessionSchema>;
export type Session = typeof sessionsTable.$inferSelect;
