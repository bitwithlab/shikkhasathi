# syntax=docker/dockerfile:1.7
FROM node:22-alpine AS base
RUN corepack enable
WORKDIR /app

FROM base AS deps
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY Web/shared/package.json Web/shared/
COPY Web/server/package.json Web/server/
COPY Web/client/package.json Web/client/
RUN pnpm install --frozen-lockfile

FROM deps AS build
COPY . .
RUN node scripts/gen-tokens.mjs && pnpm --filter @shikkhasathi/client build && pnpm --filter @shikkhasathi/server build
RUN pnpm deploy --filter @shikkhasathi/server --prod --legacy /out

FROM node:22-alpine AS runtime
WORKDIR /app/Web/server
ENV NODE_ENV=production
COPY --from=build /out ./
COPY --from=build /app/Web/server/src/database/core/migrations ./dist/database/core/migrations
COPY --from=build /app/Web/server/src/database/content/migrations ./dist/database/content/migrations
COPY --from=build /app/Web/client/dist /app/Web/client/dist
USER node

FROM runtime AS server
EXPOSE 3000
CMD ["node", "dist/server.js"]

FROM runtime AS worker
CMD ["node", "dist/worker.js"]
