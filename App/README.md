# Mobile App

The mobile application is a separate codebase and build/release target.
It must use the same backend API as the website:

`https://your-domain.com/api/v1`

Keep API base URL configurable by environment/build flavor. Do not duplicate
business rules or create a second mobile-only backend.
