#!/usr/bin/env bash
# PostToolUse check for Edit and Write. Catches the edits that build clean and fail only at runtime.
set -euo pipefail

path=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
[ -n "$path" ] && [ -f "$path" ] || exit 0

case "$path" in
  */composeResources/drawable*/*.xml)
    # CMP's vector parser resolves neither; both crash at runtime with a clean build.
    if hits=$(grep -nE '\?attr/|@android:' "$path"); then
      jq -n --arg r "$path uses ?attr/ or @android: references, which Compose Multiplatform cannot resolve (runtime crash, clean build). Use literal colours such as #FFFFFFFF and let Icon tint from LocalContentColor. Offending lines:
$hits" '{decision: "block", reason: $r}'
    fi
    ;;
  */web/src/commonMain/resources/styles.css | */ui/theme/WebSkin.kt)
    # The page colour is duplicated so it paints before any Kotlin runs.
    jq -n '{hookSpecificOutput: {hookEventName: "PostToolUse", additionalContext: "styles.css duplicates the page colour from WebSkin.kt (it paints before any Kotlin runs). If you changed that colour, change it in the other file too, or every cold load flashes the wrong colour."}}'
    ;;
esac
exit 0
