#!/usr/bin/env bash
# PreToolUse on `git commit`: CI runs ktfmtCheck, so fail the commit here instead of a CI round trip.
# Only runs Gradle when Kotlin sources are staged.
set -uo pipefail

cd "$CLAUDE_PROJECT_DIR" || exit 0
staged=$(git diff --cached --name-only --diff-filter=ACMR)
grep -Eq '\.kts?$' <<<"$staged" || exit 0

failed=()
if grep -Ev '^build-logic/' <<<"$staged" | grep -Eq '\.kts?$'; then
  ./gradlew -q ktfmtCheck >/dev/null 2>&1 || failed+=("./gradlew ktfmtFormat")
fi
if grep -Eq '^build-logic/.*\.kts?$' <<<"$staged"; then
  ./gradlew -q -p build-logic ktfmtCheck >/dev/null 2>&1 || failed+=("./gradlew -p build-logic ktfmtFormat")
fi

if [ ${#failed[@]} -gt 0 ]; then
  jq -n --arg r "ktfmtCheck failed on staged Kotlin. Run: ${failed[*]}, then re-stage the files by path and commit again." \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $r}}'
fi
exit 0
