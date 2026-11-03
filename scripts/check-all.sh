#!/usr/bin/env bash
# Runs every check the CI runs (web + mobile JVM tests). Usage: bash scripts/check-all.sh
set -euo pipefail
cd "$(dirname "$0")/.."
node scripts/gen-tokens.mjs
pnpm lint
pnpm typecheck
pnpm test
pnpm --filter @shikkhasathi/client build
node scripts/check-bundle.mjs
pnpm gen:openapi
if [ -x App/mobile/gradlew ]; then (cd App/mobile && ./gradlew --no-daemon check); fi
echo "✓ all checks passed"
