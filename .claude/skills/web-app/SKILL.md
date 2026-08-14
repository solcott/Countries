---
name: web-app
description: The browser app (:web), which targets js and wasmJs from one module, plus the service worker that makes it load offline. Use when editing anything under web/, touching browser history and routing, the SQL.js worker, npm/yarn lockfiles for the web targets, or sw.js. Covers why commonMain is the web source set and why nothing is precached.
---

# The web app

Browser entry point and offline behaviour. See `AGENTS.md` for the module map and the project-wide
conventions this sits inside.

## The `web` module

The browser app, targeting **both** `js` and `wasmJs` from one module. Four things about it are
not obvious:

- **It does not apply `kmp-library`.** That convention is for libraries: it adds android, jvm,
  ios and macos targets, and it never calls `binaries.executable()` — which is what turns a klib
  into a webpack bundle. `web/build.gradle.kts` declares its two targets itself — and, because
  the convention is not there to do it, wires `kotlin("test")` into `commonTest` by hand. It
  still applies
  `formatting`, and it applies `metro` so `createGraph<ComposeGraph>()` resolves, exactly as
  `:app` does.
- **`commonMain` *is* the web source set.** With only js and wasmJs on the module, the metadata
  compilation resolves against `kotlinx-browser` and `org.w3c.dom`, so `window`, `history` and
  the DOM event types are usable from common code with no `expect`/`actual` — the same thing
  Compose Multiplatform does in its own `webMain`. There is no `src/webMain` here, and adding one
  would buy nothing. `index.html` and `styles.css` live in `src/commonMain/resources/` and both
  target distributions pick them up.
- **`main()` mounts through `ComposeViewport(viewportContainerId = "composeApp")`** from
  `androidx.compose.ui.window`, which is `@ExperimentalComposeUiApi`. It waits for the DOM and,
  on wasm, for the runtime, so no `onWasmReady` wrapper is needed. The container must be sized by
  CSS — Compose measures its viewport from the element, and a zero-height container renders
  nothing with no error.
- **Browser history is hand-written**, in `BrowserHistory.kt`. Circuit ships no web history
  integration, and Compose Multiplatform's web `BackHandler` is driven by a
  `NavigationEventDispatcher` that browser `popstate` does not feed. The binding is
  bidirectional: pushes become `pushState`, in-app pops become `history.back()`, and `popstate`
  drives the backstack. `Routes.kt` owns the URL scheme — hash routes (`#/`,
  `#/country/{code}`), because a static bundle has no server to rewrite paths back to
  `index.html`.

  **The decision itself lives in `historyAction()` (`HistoryAction.kt`), which is pure and
  tested — change the navigation rules there, not in the effect.** `BrowserHistory` only
  executes the `HistoryAction` it returns. That split exists because the rule set is a six-way
  precedence table that produced two bugs while it was welded to `window.history` and therefore
  untestable. Note especially that the first reconciliation *seeds* history from the backstack
  (`prevDepth == UNRECONCILED`): the document has one entry however deep the URL seeded the
  backstack, so a deep link needs the list synthesised underneath it.

Both web targets need **Chrome** installed to run, and `devNpm("copy-webpack-plugin")` is
declared per target because `npm()`/`devNpm()` are only available to JS-family source sets.
The js and wasm npm stores have **separate lockfiles and separate upgrade tasks** —
`kotlinUpgradeYarnLock` and `kotlinWasmUpgradeYarnLock`. Adding an npm dependency needs both.

Also add `binaries.executable()` to the `js` and `wasmJs` targets of any **Compose library**
module — see `presenter/build.gradle.kts`. CMP 1.12 added
`checkComposeUiTestConfigurationFor{Js,WasmJs}`, which hard-fails any module whose browser test
bundle reaches skiko without an executable binary to bundle it into. It fires off the target's
test task existing, not off there being test sources, and there is no opt-out property.


## Offline, and the service worker

`web/src/commonMain/resources/sw.js`, registered from `ServiceWorker.kt`. **This is the thing that
makes the app load with no network at all.** The persistent Apollo cache only helps once the page
is running; without a service worker an offline reload never gets that far, because the bundle
itself cannot be fetched.

| Request | Strategy | Why |
| --- | --- | --- |
| Same-origin `GET` | stale-while-revalidate | The app shell, the hashed `.wasm` chunks, `composeResources` |
| `fonts.gstatic.com` | cache-first | Immutable, and what keeps flags and non-Latin text from reverting to tofu offline |
| GraphQL `POST` | network-first, cache fallback | The Cache API ignores POSTs, so responses are keyed by a hash of the request body |

**Nothing is precached.** The bundle filenames are content-hashed, so a hard-coded manifest would
rot on every build; the shell is cached as it is first requested instead. The cost is that the app
needs one online visit before it works offline.

Two practical notes:

- **Bump `CACHE_VERSION` to evict everything.** `activate` deletes every cache that is not current.
- **Under `webpack-dev-server`, stale-while-revalidate can serve one-load-stale content.** That is
  the strategy working, not a build bug — `skipWaiting()` means the next reload picks up the new
  bundle. Verify offline behaviour against a *distribution* served by a static file server, not
  against the dev server.

