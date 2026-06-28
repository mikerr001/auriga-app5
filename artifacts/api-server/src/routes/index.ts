import { Router, type IRouter } from "express";
import healthRouter from "./health";
import waitlistRouter from "./waitlist";
import devicesRouter from "./devices";
import hazardsRouter from "./hazards";
import sessionsRouter from "./sessions";
import statsRouter from "./stats";

const router: IRouter = Router();

router.use(healthRouter);
router.use(waitlistRouter);
router.use(devicesRouter);
router.use(hazardsRouter);
router.use(sessionsRouter);
router.use(statsRouter);

export default router;
