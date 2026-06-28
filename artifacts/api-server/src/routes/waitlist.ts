import { Router, type IRouter } from "express";
import { eq, sql } from "drizzle-orm";
import { db, waitlistTable } from "@workspace/db";
import {
  JoinWaitlistBody,
  JoinWaitlistResponse,
  ListWaitlistResponse,
  GetWaitlistStatsResponse,
} from "@workspace/api-zod";

const router: IRouter = Router();

router.post("/waitlist", async (req, res): Promise<void> => {
  const parsed = JoinWaitlistBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }

  const existing = await db
    .select()
    .from(waitlistTable)
    .where(eq(waitlistTable.email, parsed.data.email))
    .limit(1);

  if (existing.length > 0) {
    res.status(409).json({ error: "Email already on waitlist" });
    return;
  }

  const [entry] = await db.insert(waitlistTable).values(parsed.data).returning();
  res.status(201).json(JoinWaitlistResponse.parse({
    ...entry,
    createdAt: entry.createdAt.toISOString(),
  }));
});

router.get("/waitlist", async (_req, res): Promise<void> => {
  const entries = await db
    .select()
    .from(waitlistTable)
    .orderBy(waitlistTable.createdAt);

  res.json(ListWaitlistResponse.parse(
    entries.map((e) => ({ ...e, createdAt: e.createdAt.toISOString() }))
  ));
});

router.get("/waitlist/stats", async (_req, res): Promise<void> => {
  const total = await db
    .select({ count: sql<number>`cast(count(*) as int)` })
    .from(waitlistTable);

  const byCountryRaw = await db
    .select({
      label: waitlistTable.country,
      count: sql<number>`cast(count(*) as int)`,
    })
    .from(waitlistTable)
    .groupBy(waitlistTable.country);

  const bySourceRaw = await db
    .select({
      label: waitlistTable.source,
      count: sql<number>`cast(count(*) as int)`,
    })
    .from(waitlistTable)
    .groupBy(waitlistTable.source);

  res.json(GetWaitlistStatsResponse.parse({
    total: total[0]?.count ?? 0,
    byCountry: byCountryRaw.map((r) => ({ label: r.label ?? "Unknown", count: r.count })),
    bySource: bySourceRaw.map((r) => ({ label: r.label ?? "Unknown", count: r.count })),
  }));
});

export default router;
