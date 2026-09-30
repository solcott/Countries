---
paths:
  - "network/**"
---

# `:network` — Apollo, the normalized cache, the SQL.js worker

The endpoint is the public API at `https://countries.trevorblades.com/`.

## Apollo

- The Apollo Gradle plugin detects KMP by itself: operations in `src/commonMain/graphql/`, generated
  code attached to `commonMain`, no `srcDir` or output wiring. It also adds `-lsqlite3` to native
  binaries once it sees `normalized-cache-sqlite`.
- **Generated code is build output.** Never hand-edit it, never commit it, never import the
  generated package outside `:network`. `:network` maps generated types to `model` types and returns
  only the latter. To change what is fetched, change the `.graphql` files.
- **Per-platform client config goes through `ApolloClient.Builder.platformConfiguration()`**, an
  `expect` extension in `network/src/commonMain`. Add HTTP engines, interceptors, etc. there — do not
  fork the provider. Endpoint, in-memory tier and the Metro provider stay in `commonMain`.
- Caching is Apollo's normalized cache, here. Do not add a second layer anywhere above it.
- `NetworkProviders` is `@ContributesTo(AppScope::class)` and must stay `api` on the graph module.

## Where the cache lives

`SqlNormalizedCacheFactory(name)` is an `expect` in Apollo; storage differs per target:

| Target | Storage |
| --- | --- |
| Android | `cacheDir`, via an `androidx.startup` initializer in the AAR |
| JVM | `~/.apollo` |
| Apple | Application Support |
| js / wasmJs | SQLDelight's SQL.js web-worker driver — **the name is ignored** |

The web driver's two npm dependencies (on `jsMain` and `wasmJsMain`) are **pinned to the SQLDelight
version `normalized-cache-sqlite` depends on (currently 2.1.0), not the latest.** Renovate cannot see
that transitive pin, so it has SQLDelight disabled — bump it by hand alongside the cache.

**`sql.js` is declared twice and both must match:** `npm("sql.js", …)` in `network/build.gradle.kts`
(the copied `sql-wasm.wasm`) and the worker's `package.json` (the JS glue). A mismatch makes
`initSqlJs()` hang forever — a silent spinner. Renovate sees only one of them; bump both by hand.

A browser *application* also needs the `webpack.config.d/` copy of `sql.js`'s `.wasm` (`web/webpack.config.d/sqljs.js`)
— without it the build is clean and the worker 404s at runtime. A library module does not.

## The SQL.js worker — `network/npm/countries-sqljs-idb-worker/`

**`createDefaultWebWorkerDriver()` must not come back.** The reference worker does
`new SQL.Database()` and never persists it. Ours loads from IndexedDB at startup and writes
`db.export()` back, debounced, after each transaction. `NetworkProviders.{js,wasmJs}.kt` build the
`WebWorkerDriver` around it by hand.

- **It is a local npm package, not a loose `.js` file.** `new Worker(new URL(…))` must resolve at
  bundle time; a bare specifier out of `node_modules` is the only shape that works from a library.
  Both `jsMain` and `wasmJsMain` declare it.
- **The `exec` response must stay `res[0] ?? { values: [] }`.** `db.exec` returns `[]` for an empty
  `SELECT`; anything richer makes a cache miss look like a row to SQLDelight's cursor.
- **The database name travels as the worker's own name** (`new Worker(url, { name })`) — the
  protocol has no field for it. It keys the IndexedDB snapshot.
- **The `Worker` must not move up into `webMain`.** SQLDelight's `expect class Worker` is a
  typealias to `org.w3c.dom.Worker`, which only expands in a *platform* compilation;
  `compileWebMainKotlinMetadata` sees an opaque expect class and fails. The seam is
  `persistentSqlJsDriver(): SqlDriver` for that reason. **That failure blocks `assemble` for
  `:network` and everything above it, and neither web target's own compile task reproduces it.**

Persisting the cache is not what makes the web app work offline — the service worker is (see
`web.md`).

## Verify

- `./gradlew :network:assemble` — not `:network:compileKotlinJs`; only `assemble` catches the
  `webMain` metadata trap.
- `./gradlew :repository:allTests` — the mapping tests live there; `:network` has none.
- Worker changed: `./gradlew :web:wasmJsBrowserDevelopmentRun` and confirm the IndexedDB snapshot
  survives a reload.
