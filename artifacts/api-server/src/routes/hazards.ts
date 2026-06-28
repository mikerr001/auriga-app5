import { Router, type IRouter } from "express";
import { eq, desc, sql } from "drizzle-orm";
import { db, hazardsTable } from "@workspace/db";
import {
  ReportHazardBody,
  ReportHazardResponse,
  ListHazardsResponse,
  ListHazardsQueryParams,
  GetHazardSummaryResponse,
} from "@workspace/api-zod";

const router: IRouter = Router();

router.get("/hazards/summary", async (_req, res): Promise<void> => {
  const totalRaw = await db
    .select({ count: sql<number>`cast(count(*) as int)` })
    .from(hazardsTable);

  const avgRaw = await db
    .select({ avg: sql<number>`coalesce(avg(confidence), 0)` })
    .from(hazardsTable);

  const byTypeRaw = await db
    .select({
      label: hazardsTable.hazardType,
      count: sql<number>`cast(count(*) as int)`,
    })
    .from(hazardsTable)
    .groupBy(hazardsTable.hazardType)
    .orderBy(desc(sql`count(*)`));

  const recentRaw = await db
    .select()
    .from(hazardsTable)
    .orderBy(desc(hazardsTable.detectedAt))
    .limit(10);

  res.json(GetHazardSummaryResponse.parse({
    totalEvents: totalRaw[0]?.count ?? 0,
    avgConfidence: avgRaw[0]?.avg ?? 0,
    byType: byTypeRaw,
    recentEvents: recentRaw.map((h) => ({
      ...h,
      detectedAt: h.detectedAt.toISOString(),
    })),
  }));
});

router.get("/hazards", async (req, res): Promise<void> => {
  const queryParsed = ListHazardsQueryParams.safeParse(req.query);
  if (!queryParsed.success) {
    res.status(400).json({ error: queryParsed.error.message });
    return;
  }

  const { deviceId, limit } = queryParsed.data;

  let query = db.select().from(hazardsTable).$dynamic();
  if (deviceId != null) {
    query = query.where(eq(hazardsTable.deviceId, deviceId));
  }
  query = query.orderBy(desc(hazardsTable.detectedAt)).limit(limit ?? 100);

  const hazards = await query;
  res.json(ListHazardsResponse.parse(
    hazards.map((h) => ({ ...h, detectedAt: h.detectedAt.toISOString() }))
  ));
});

router.post("/hazards", async (req, res): Promise<void> => {
  const parsed = ReportHazardBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }

  const [hazard] = await db.insert(hazardsTable).values(parsed.data).returning();
  res.status(201).json(ReportHazardResponse.parse({
    ...hazard,
    detectedAt: hazard.detectedAt.toISOString(),
  }));
});

export default router;
