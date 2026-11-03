# ShikkhaSathi

ShikkhaSathi is organized as one deployable web platform plus a separate mobile app.

## Main structure

- `Web/client` — public website, student dashboard, seller panel and admin panel.
- `Web/server` — the single backend that serves the built website and exposes all APIs.
- `Web/shared` — shared API contracts and types.
- `App/mobile` — independent Android/iOS application source code.
- `Reference` — design references, research and approved assets.
- `docs` — architecture, API, database and deployment documentation.

## One domain and one port

In production, the web server should serve both the frontend and API:

- `/` — public website
- `/courses/...` — marketplace/course pages
- `/student/...` — student portal
- `/seller/...` — seller portal
- `/admin/...` — admin panel
- `/api/v1/...` — API used by the website and mobile app

The mobile app is built and distributed separately, but calls the same `/api/v1` backend.

The server must serve the frontend build for browser routes while reserving `/api/*`
for API routes. Configure the production server/reverse proxy to expose one public
origin and port. Do not deploy a second public API origin unless there is a deliberate
scaling or infrastructure reason.

## Development

The development setup should also expose one browser origin/port. The server can
proxy frontend dev requests internally, or the frontend can be built and served by
the backend. Internal processes may use different ports, but users and clients should
use one public origin.

See `docs/architecture/deployment-and-routing.md`.
