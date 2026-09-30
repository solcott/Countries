---
paths:
  - "web/**"
---

# `:web` — the browser app (js + wasmJs)

- **It does not apply `kmp-library`.** That convention adds android/jvm/apple targets and never
  calls `binaries.executable()`, which is what turns a klib into a webpack bundle.
  `web/build.gradle.kts` declares its two targets itself and wires `kotlin("test")` into
  `commonTest` by hand. It still applies `formatting` and `metro`.
- **`commonMain` *is* the web source set.** With only js and wasmJs, `window`, `history` and DOM
  types are usable from common code with no `expect`/`actual`. There is no `src/webMain`; adding one
  buys nothing. `index.html`, `styles.css` and `sw.js` live in `src/commonMain/resources/`.
- **`main()` mounts via `ComposeViewport(viewportContainerId = "composeApp")`**
  (`@ExperimentalComposeUiApi`, no `onWasmReady` wrapper needed). **The container must be sized by
  CSS** — a zero-height container renders nothing, with no error.
- **`styles.css` duplicates the page colour from `WebSkin`** because it paints before any Kotlin
  runs. Change one, change the other, or every cold load flashes the wrong colour.
- **Browser history is hand-written** in `BrowserHistory.kt` (Circuit has no web history, and CMP's
  web `BackHandler` is not fed by `popstate`). `Routes.kt` owns the hash-route scheme (`#/`,
  `#/country/{code}`).
  - **Change navigation rules in `historyAction()` (`HistoryAction.kt`), which is pure and tested —
    not in `BrowserHistory`,** which only executes the returned `HistoryAction`. The first
    reconciliation *seeds* history from the backstack (`prevDepth == UNRECONCILED`), so a deep link
    gets its list entry synthesised underneath it.
  - `main()` hoists the backstack to bind it to `window.history`, which is why it passes
    `circuitSaver = graph.circuitSaver` — `LocalCircuitSaver` is not in scope yet.
- **npm:** `devNpm("copy-webpack-plugin")` is declared per target (`npm()`/`devNpm()` exist only on
  JS-family source sets). js and wasmJs have **separate lockfiles** — run both
  `kotlinUpgradeYarnLock` and `kotlinWasmUpgradeYarnLock`.
- Both targets need **Chrome** installed to run and test.

## The service worker — `sw.js`, registered from `ServiceWorker.kt`

**This is what makes the app load offline**, not the Apollo cache — without it an offline reload
never fetches the bundle.

| Request | Strategy | Why |
| --- | --- | --- |
| Same-origin `GET` | stale-while-revalidate | shell, hashed `.wasm` chunks, `composeResources` |
| `fonts.gstatic.com` | cache-first | immutable; keeps flags and non-Latin text from reverting to tofu offline |
| GraphQL `POST` | network-first, cache fallback | Cache API ignores POSTs, so keyed by a hash of the body |

- **Nothing is precached** — filenames are content-hashed, so a manifest would rot. One online
  visit is needed before offline works.
- **Bump `CACHE_VERSION` to evict everything.**
- Under `webpack-dev-server`, stale-while-revalidate can serve one-load-stale content; that is the
  strategy working. **Verify offline behaviour against a distribution** served by a static file
  server, not the dev server.

## Commands

```
# Browser app — serves on http://localhost:8080
./gradlew :web:wasmJsBrowserDevelopmentRun
./gradlew :web:jsBrowserDevelopmentRun
./gradlew :web:wasmJsBrowserDistribution   # → web/build/dist/wasmJs/productionExecutable
./gradlew :web:jsBrowserDistribution       # → web/build/dist/js/productionExecutable
```

## Verify

`./gradlew :web:wasmJsBrowserDistribution :web:allTests`
