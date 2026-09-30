#!/usr/bin/env bash
# PreToolUse guard for Bash, Edit and Write. Denies the operations this repo documents as never-do,
# each of which fails later and somewhere else rather than at the point of the mistake.
set -euo pipefail

input=$(cat)
tool=$(jq -r '.tool_name // empty' <<<"$input")

deny() {
  jq -n --arg r "$1" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $r}}'
  exit 0
}

case "$tool" in
  Bash)
    cmd=$(jq -r '.tool_input.command // empty' <<<"$input")
    # `git add -A` / `.` / `--all` sweeps Xcode xcuserdata/ on branches below the iOS PR's gitignore.
    if grep -Eq '(^|[;&|[:space:]])git[[:space:]]+add([[:space:]]+[^;&|]*)?[[:space:]](-A|--all|\.)([[:space:]]|$|[;&|])' <<<"$cmd"; then
      deny "Stage by explicit path, never 'git add -A', '--all' or '.': on some branches the sweep picks up Xcode xcuserdata/. Check 'git status --short' first."
    fi
    # Stacked PRs: a bare force push can clobber a rebase done elsewhere.
    if grep -Eq '(^|[;&|[:space:]])git[[:space:]]+push([[:space:]]|$)' <<<"$cmd" &&
      grep -Eq '[[:space:]](--force|-f)([[:space:]]|$)' <<<"$cmd" &&
      ! grep -q -- '--force-with-lease' <<<"$cmd"; then
      deny "Use 'git push --force-with-lease', never a bare --force or -f."
    fi
    ;;
  Edit | Write | MultiEdit)
    path=$(jq -r '.tool_input.file_path // empty' <<<"$input")
    case "$path" in
      */build/*)
        deny "$path is build output (Apollo generated code included). Change the source or the .graphql operation instead." ;;
      */kotlin-js-store/*.lock)
        deny "Never hand-edit the npm lockfiles. Regenerate both with './gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock'." ;;
    esac
    ;;
esac
exit 0
