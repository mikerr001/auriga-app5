import { Router, type IRouter } from "express";
import { eq } from "drizzle-orm";
import { db, devicesTable } from "@workspace/db";
import {
  RegisterDeviceBody,
  RegisterDeviceResponse,
  ListDevicesResponse,
  GetDeviceParams,
  GetDeviceResponse,
} from "@workspace/api-zod";

const router: IRouter = Router();

router.get("/devices", async (_req, res): Promise<void> => {
  const devices = await db
    .select()
    .from(devicesTable)
    .orderBy(devicesTable.createdAt);

  res.json(ListDevicesResponse.parse(
    devices.map((d) => ({
      ...d,
      createdAt: d.createdAt.toISOString(),
      lastSeenAt: d.lastSeenAt ? d.lastSeenAt.toISOString() : null,
    }))
  ));
});

router.post("/devices", async (req, res): Promise<void> => {
  const parsed = RegisterDeviceBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }

  const [device] = await db.insert(devicesTable).values(parsed.data).returning();
  res.status(201).json(RegisterDeviceResponse.parse({
    ...device,
    createdAt: device.createdAt.toISOString(),
    lastSeenAt: device.lastSeenAt ? device.lastSeenAt.toISOString() : null,
  }));
});

router.get("/devices/:deviceId", async (req, res): Promise<void> => {
  const params = GetDeviceParams.safeParse(req.params);
  if (!params.success) {
    res.status(400).json({ error: params.error.message });
    return;
  }

  const [device] = await db
    .select()
    .from(devicesTable)
    .where(eq(devicesTable.id, params.data.deviceId))
    .limit(1);

  if (!device) {
    res.status(404).json({ error: "Device not found" });
    return;
  }

  res.json(GetDeviceResponse.parse({
    ...device,
    createdAt: device.createdAt.toISOString(),
    lastSeenAt: device.lastSeenAt ? device.lastSeenAt.toISOString() : null,
  }));
});

export default router;
