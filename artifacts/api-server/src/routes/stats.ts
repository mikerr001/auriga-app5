import { Router, type IRouter } from "express";
import { desc, sql } from "drizzle-orm";
import { db, waitlistTable, devicesTable, hazardsTable, sessionsTable } from "@workspace/db";
import { GetDashboardStatsResponse } from "@workspace/api-zod";

const router: IRouter = Router();

router.get("/stats/dashboard", async (_req, res): Promise<void> => {
  const [waitlistTotal, devicesTotal, hazardsTotal, sessionsTotal] = await Promise.all([
    db.select({ count: sql<number>`cast(count(*) as int)` }).from(waitlistTable),
    db.select({ count: sql<number>`cast(count(*) as int)` }).from(devicesTable),
    db.select({ count: sql<number>`cast(count(*) as int)` }).from(hazardsTable),
    db.select({ count: sql<number>`cast(count(*) as int)` }).from(sessionsTable),
  ]);

  const recentHazards = await db
    .select()
    .from(hazardsTable)
    .orderBy(desc(hazardsTable.detectedAt))
    .limit(5);

  const topHazardTypes = await db
    .select({
      label: hazardsTable.hazardType,
      count: sql<number>`cast(count(*) as int)`,
    })
    .from(hazardsTable)
    .groupBy(hazardsTable.hazardType)
    .orderBy(desc(sql`count(*)`))
    .limit(5);

  res.json(GetDashboardStatsResponse.parse({
    waitlistTotal: waitlistTotal[0]?.count ?? 0,
    devicesTotal: devicesTotal[0]?.count ?? 0,
    hazardsTotal: hazardsTotal[0]?.count ?? 0,
    sessionsTotal: sessionsTotal[0]?.count ?? 0,
    recentHazards: recentHazards.map((h) => ({
      ...h,
      detectedAt: h.detectedAt.toISOString(),
    })),
    topHazardTypes,
  }));
});

export default router;
