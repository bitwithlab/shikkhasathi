# ShikkhaSathi: One Origin / One Port Architecture

## Public routing

| Path | Served by | Purpose |
|---|---|---|
| `/` | Web server -> client build | Public website |
| `/courses/*` | Web server -> client build | Marketplace |
| `/student/*` | Web server -> client build | Student panel |
| `/seller/*` | Web server -> client build | Seller panel |
| `/admin/*` | Web server -> client build | Admin panel |
| `/api/v1/*` | Web server -> API router | Shared API for web and mobile |

## Runtime rule

Use one public origin and port. The backend server should serve the built frontend
and handle API requests. A reverse proxy may sit in front, but it should expose one
public origin/port and route internally.

During development, internal tooling may use separate processes/ports. If so, use a
development proxy so the browser still uses one origin. This is a development
implementation detail, not a requirement to split production deployments.

## Mobile rule

The mobile app is a separate project and release artifact. It calls the exact same
versioned API as the web client. Keep the API base URL configurable and avoid
hardcoding localhost in release builds.

## Security and operations

- Enforce role permissions on the backend for every protected operation.
- Validate input and authenticate API requests.
- Keep secrets in environment variables/secret storage.
- Store private uploads outside the public static directory.
- Configure CORS only for origins that genuinely need cross-origin access.
- Add API versioning, logging, rate limits, backups and automated tests.
