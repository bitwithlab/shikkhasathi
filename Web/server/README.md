# Web Server

The server is the single runtime entry point for the web platform.

Responsibilities:
1. Register `/api/v1/*` API routes.
2. Authenticate and authorize student, seller, instructor and admin requests.
3. Serve the compiled `Web/client` assets in production.
4. Provide SPA/browser-route fallback only for non-API paths.
5. Keep uploads, secrets and private files outside the public static directory.

Never rely only on frontend route hiding for authorization. Enforce permissions
on every protected API endpoint.
