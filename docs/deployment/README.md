# Deployment

Deploy the web client build and API server as one web application/public origin.
The server serves static client assets and handles `/api/v1/*` on the same public
port. Deploy the mobile app separately through its app distribution pipeline.
