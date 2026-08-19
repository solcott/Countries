---
name: pr-review
description: How to read and act on GitHub review comments on this repo's PRs — the four comment surfaces, the fix/reply-with-SHA/resolve loop, and the rebase traps that come with stacked PRs. Use when asked to check, address, or respond to PR comments, or when rebasing a stack. `gh pr view --comments` misses inline comments entirely, so read the REST and GraphQL endpoints instead.
---

# Handling PR review comments

The repo is `solcott/Countries`. See `AGENTS.md` for the project conventions the fixes themselves
have to satisfy.

## Read all four surfaces — `gh pr view --comments` is not enough

That command has printed nothing while the REST endpoints held data, and **inline line comments are
the easiest to miss and usually the most substantive.** Read all four:

```sh
PR=<number>

# 1. Conversation comments (the PR's issue timeline)
gh api repos/solcott/Countries/issues/$PR/comments \
  --jq '.[] | {user: .user.login, body: .body}'

# 2. Inline line comments — the ones the shorthand misses
gh api repos/solcott/Countries/pulls/$PR/comments \
  --jq '.[] | {user: .user.login, path, line, body: .body}'

# 3. Review summaries (approve / request-changes bodies)
gh api repos/solcott/Countries/pulls/$PR/reviews \
  --jq '.[] | select(.body != "") | {user: .user.login, state, body: .body}'

# 4. Thread IDs plus resolved/outdated state — GraphQL only
gh api graphql -f query='
  query($owner:String!, $repo:String!, $pr:Int!) {
    repository(owner:$owner, name:$repo) {
      pullRequest(number:$pr) {
        reviewThreads(first:100) {
          nodes {
            id isResolved isOutdated
            comments(first:1) { nodes { author { login } path line body } }
          }
        }
      }
    }
  }' -F owner=solcott -F repo=Countries -F pr=$PR \
  --jq '.data.repository.pullRequest.reviewThreads.nodes[]
        | select(.isResolved | not)
        | {id, outdated: .isOutdated, c: .comments.nodes[0]}'
```

Surface 4 is the working queue: **the unresolved threads.** An `isOutdated` thread points at a line
that has since changed — still worth reading, often already fixed.

## The loop: fix → reply with the SHA → resolve

For each unresolved thread:

1. **Fix it in one commit of its own.** One commit per comment, so the SHA in the reply means only
   that fix and nothing else.
2. **Reply in the thread**, saying what changed and citing the commit:

   ```sh
   gh api repos/solcott/Countries/pulls/$PR/comments/<comment_id>/replies \
     -f body='Fixed in abc1234 — <one line on what changed>.'
   ```

3. **Resolve the thread.** `gh` has no command for this; it is a GraphQL mutation on the thread ID
   from surface 4:

   ```sh
   gh api graphql -f query='
     mutation($id:ID!) { resolveReviewThread(input:{threadId:$id}) { thread { isResolved } } }' \
     -F id=<thread_id>
   ```

Resolving matters because it keeps the unresolved count equal to the real remaining queue. A fixed
comment left unresolved makes the next pass re-read work that is already done.

## Disagree by replying, not by implementing

**If a comment rests on a checkable claim that is wrong, post the reasoning and leave the code
unchanged until the user responds.** Do not implement a change you believe is mistaken and then
mention the doubt afterwards.

This is not politeness — it has paid off concretely. The `:desktop` build script once carried a
comment asserting that `build-logic`'s `Versions` was unreachable from a module script. That was
false, and finding it out changed the design.

If the user reaffirms the comment after your pushback, implement it as asked and say plainly that
you are doing so.

## Route the fix to the branch that owns the code

Never land a fix on a descendant branch to dodge a rebase. The change belongs on the branch where
the code lives, and the stack gets rebased.

## Rebasing the stack — three traps

This repo uses stacked PRs. Each of these has cost real time, and **none of them fails at the point
of the mistake** — they surface later as a confusing build error or an unwanted file in a commit.

- **Never `git add -A`.** The `xcuserdata/` gitignore rule is introduced *by* the iOS PR, so on any
  branch at or below it the sweep picks up Xcode per-user files. This has happened twice. Stage by
  explicit path and check `git status --short` before every commit.
- **Wipe `~/Library/Developer/Xcode/DerivedData/Countries-*` when switching between branches that
  build the Apple framework differently.** They emit incompatible `CountriesKit` modules into the
  same path; a stale artifact once produced a false "minimum deployment target of iOS 18.0"
  failure.
- **Check local branches against their remotes before starting.** They have been found diverged
  with identical content but different SHAs, from a rebase done on GitHub's side. Verify with
  `git diff --stat <local> <remote>` — empty means same content — then reset to the remote, which
  is what the PRs actually point at.

Force-push descendants with `--force-with-lease`, never a bare `--force`.

Editing a file that differs between branches guarantees a conflict on the downstream rebase.
Expect it rather than treating it as a problem.

**A rebase invalidates SHAs you have already cited in replies.** If you rebase after replying, the
`abc1234` in a thread no longer exists. Finish the fix/reply/resolve loop for a branch before
rebasing it, or note the new SHA in a follow-up reply.

## Before pushing

Run the `verify` skill to work out the minimal task set for what actually changed, and let
`gradle-runner` / `xcode-runner` run it. Do not push a fix you have not built.
