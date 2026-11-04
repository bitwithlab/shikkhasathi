#!/usr/bin/env bash
# Runs every check the CI runs: backend (Maven), website (pnpm), mobile (Gradle, JVM tests + lint).
# Usage: bash scripts/check-all.sh     (needs Java 21, Node 22 + pnpm, local Postgres 16 + Redis for backend tests)
set -euo pipefail
cd "$(dirname "$0")/.."
node scripts/gen-tokens.mjs
git diff --exit-code -- website/src/styles/tokens app/shared/src/commonMain/kotlin/com/shikkhasathi/core/designsystem/Tokens.kt

(cd backend && ./mvnw -B verify)

(cd website && pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm check:bundle)

if [ -x app/gradlew ]; then (cd app && ./gradlew --no-daemon :shared:desktopTest ktlintCheck detekt); fi
echo "✓ all checks passed"
