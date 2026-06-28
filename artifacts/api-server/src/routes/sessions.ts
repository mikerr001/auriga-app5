import { Router, type IRouter } from "express";
import { desc } from "drizzle-orm";
import { db, sessionsTable } from "@workspace/db";
import {
  CreateSessionBody,
  CreateSessionResponse,
  ListSessionsResponse,
} from "@workspace/api-zod";

const router: IRouter = Router();

router.get("/sessions", async (_req, res): Promise<void> => {
  const sessions = await db
    .select()
    .from(sessionsTable)
    .orderBy(desc(sessionsTable.createdAt));

  res.json(ListSessionsResponse.parse(
    sessions.map((s) => ({ ...s, createdAt: s.createdAt.toISOString() }))
  ));
});

router.post("/sessions", async (req, res): Promise<void> => {
  const parsed = CreateSessionBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }

  const [session] = await db.insert(sessionsTable).values(parsed.data).returning();
  res.status(201).json(CreateSessionResponse.parse({
    ...session,
    createdAt: session.createdAt.toISOString(),
  }));
});

export default router;
