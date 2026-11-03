# Unified Web Platform

This is one deployable application containing the web client and backend server.

- `client/` contains all browser interfaces: public, student, seller and admin.
- `server/` serves the production client build and handles `/api/v1/*`.
- `shared/` contains API contracts/types shared across clients.

Keep one API implementation. Both the website and mobile app consume it.
