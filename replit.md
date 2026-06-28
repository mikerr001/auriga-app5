# Auriga Platform

Auriga is an offline-first spatial intelligence co-pilot for blind/visually impaired users, launching in Kenya. This monorepo hosts the marketing landing page, the internal ops/admin dashboard, and the shared backend API.

## Run & Operate

- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string

## Stack

- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Where things live

- `lib/api-spec/openapi.yaml` — OpenAPI contract (source of truth for all endpoints)
- `lib/db/src/schema/` — Drizzle table definitions (waitlist, devices, hazards, sessions)
- `lib/api-client-react/src/generated/` — React Query hooks (auto-generated, do not edit)
- `lib/api-zod/src/generated/` — Zod validation schemas (auto-generated, do not edit)
- `artifacts/api-server/src/routes/` — Express route handlers (waitlist, devices, hazards, sessions, stats)
- `artifacts/auriga-landing/` — Public marketing landing page with waitlist signup
- `artifacts/auriga-dashboard/` — Internal ops dashboard (fleet, hazards, sessions, waitlist)

## Architecture decisions

- Contract-first API: OpenAPI spec → Orval codegen → typed hooks + Zod schemas. Never hand-write these.
- All DB schema lives in `lib/db/src/schema/`; run `pnpm --filter @workspace/db run push` after any schema change, then `pnpm run typecheck:libs` to rebuild declarations.
- Routes are split by domain (waitlist.ts, devices.ts, hazards.ts, sessions.ts, stats.ts) under `artifacts/api-server/src/routes/`.
- The `/api` base path is handled by the reverse proxy; routes do not add it themselves.
- `/hazards/summary` is registered before `/hazards/:id` (static before dynamic) — do not reorder.

## Product

- **Landing page** (`/`): WCAG AAA accessible, dark dragon aesthetic, Framer Motion animations, real waitlist form wired to `POST /api/waitlist`.
- **Ops Dashboard** (`/dashboard/`): Mission Control overview (KPI tiles, hazard feed, type breakdown), Waitlist management, Device fleet tracking, Hazard event log with filtering, Field session log.
- **API** (`/api`): REST API serving all dashboard data and accepting waitlist signups from the landing page.

## User preferences

_Populate as you build — explicit user instructions worth remembering across sessions._

## Gotchas

- After editing any `lib/*` schema or source: run `pnpm run typecheck:libs` before checking artifact typechecks, or you'll get stale-declaration errors (TS2305).
- `/hazards/summary` must be registered before the dynamic `/hazards` route — Express matches first-registered.
- Body schema component names in `openapi.yaml` must be entity-shaped (`WaitlistInput`, not `JoinWaitlistBody`) to avoid TS2308 Orval collision.
- The Android app (`mikerr001/auriga-app5`) already has a working build-debug-apk.yml GitHub Actions workflow — do not modify it.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
