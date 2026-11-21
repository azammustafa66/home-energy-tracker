# Home Energy Tracker · web client

Angular 22 frontend for the Spring microservices in `../server`. It uses **bun** for installing packages and running the Angular CLI. npm is not used.

## What it does

| Page | Who sees it | Backend calls (through the gateway) |
| --- | --- | --- |
| Dashboard | everyone | `GET /devices?userId=<sub>` (all devices for admins), `GET /users/<sub>` |
| Devices | everyone | list, create, edit. Delete is admin only |
| Profile | everyone | `GET` / `PATCH /users/<sub>`: contact details, alerts on/off, alert threshold |
| Send reading | `ADMIN` or `INGESTION` role | `POST /ingestion`: one reading, or auto-send random readings |
| Users | `ADMIN` | list, create, delete user profiles |

Sign-in goes through Keycloak (Authorization Code + PKCE, via `keycloak-js`). Every API request gets the access token as a Bearer header (`src/app/core/auth.interceptor.ts`).

During development, `ng serve` proxies `/api/*` to the API gateway on `http://localhost:8000` (`proxy.conf.json`), so the gateway needs no CORS setup.

## Setup

1. Start the backend (see `../server/README.md`): `docker compose up -d` in `../server`, then the gateway, user-service and device-service (and ingestion-service for "Send reading").
2. Create the browser login client in Keycloak, once:
   ```bash
   KC_ADMIN_PASS=<KEYCLOAK_PASSWORD from server/.env> ./scripts/keycloak-web-client.sh
   ```
3. Install and run:
   ```bash
   bun install
   bun run start        # http://localhost:4200
   ```

Log in with a Keycloak user. The seeded users from `server/scripts/provision-keycloak-users.sh` work (username = the part of the email before `@`, password `password`). To see the Users and Send reading pages, give your user the `ADMIN` realm role in the Keycloak admin console.

> Your Keycloak user id (`sub`) must match a user id in user-service. The seed scripts set this up. Otherwise the Profile page tells you no profile exists.

## Scripts

All scripts run the Angular CLI on bun's runtime (`bun --bun ng …`).

| Command | What it does |
| --- | --- |
| `bun run start` | Dev server with the API proxy |
| `bun run build` | Production build into `dist/client` |
| `bun run test` | Unit tests (Vitest) |
| `bun --bun ng generate component …` | Any other Angular CLI command |

## Layout

```
src/app/
  core/       config, auth (Keycloak), HTTP interceptor, API clients, DTO types
  shared/     error banner
  features/   dashboard, devices, profile, users, ingestion
```

## Not covered yet

The backend has no REST endpoints for usage history (InfluxDB) or alert history, so the UI has no usage charts. Grafana at http://localhost:3000 shows the metrics.
