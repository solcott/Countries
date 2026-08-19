---
name: network-apollo
description: The :network module — the Apollo Kotlin client, the .graphql operations, the normalized cache, and the hand-written SQL.js IndexedDB worker the web targets use. Use when editing anything under network/, changing a GraphQL operation, touching the cache configuration, or working on the web worker in network/npm/. Covers why the worker cannot move into webMain and why that failure appears in a task neither web target runs.
---

# The network module

Apollo Kotlin, the GraphQL operations, and the normalized cache. See `AGENTS.md` for the module map
and the project-wide conventions this sits inside.

The endpoint is the public API at `https://countries.trevorblades.com/`.

## The Apollo Gradle plugin needs almost no wiring

The plugin detects the KMP plugin by itself. It reads operations from `src/commonMain/graphql/` and
attaches the generated code to `commonMain` — no `srcDir` or output wiring is needed in
`network/build.gradle.kts`. It also adds `-lsqlite3` to native binaries once it sees a
`normalized-cache-sqlite` dependency.

**Apollo generated code is build output.** Never hand-edit it, never commit it, and never import
anything from the generated package outside `:network`. `network` owns the mapping from the
generated GraphQL data classes to `model` types and returns only the latter. To change what is
fetched, change the `.graphql` operation files.

## The normalized cache, and where each platform puts it

`SqlNormalizedCacheFactory(name)` is an `expect` function *in Apollo*, so it compiles on every
target. What differs is where it stores data:

| Target | Storage |
| --- | --- |
| Android | `cacheDir`, via an `androidx.startup` initializer shipped in the AAR |
| JVM | `~/.apollo` |
| Apple | Application Support |
| js / wasmJs | SQLDelight's SQL.js web-worker driver — **and the name is ignored** |

The web driver needs two npm dependencies, declared on `jsMain` and `wasmJsMain` in
`network/build.gradle.kts`. **Pin them to the SQLDelight version that `normalized-cache-sqlite`
actually depends on (currently 2.1.0), not to the latest.**

A browser **application** module additionally needs a `webpack.config.d/` entry copying `sql.js`'s
`.wasm` into the bundle — see `web/webpack.config.d/sqljs.js`. Nothing in the Kotlin sources
references that file, so without the copy step the build is clean and the worker 404s at runtime.
That is not required for a library module, so `:network` does not have one.

## The SQL.js worker

**Web uses its own SQL.js worker, and `createDefaultWebWorkerDriver()` must not come back.** SQL.js
has no storage of its own — the database is a block of memory you are responsible for saving — and
the reference worker, `@cashapp/sqldelight-sqljs-worker`, does `new SQL.Database()` and never
writes it anywhere. On that worker the SQLite tier is a second in-memory cache behind the first
one, at the cost of a 600 KB wasm blob.

`network/npm/countries-sqljs-idb-worker/` is that worker with a persistence layer: it loads the
database from IndexedDB at startup and writes `db.export()` back, debounced, after each
transaction. `NetworkProviders.{js,wasmJs}.kt` build the `WebWorkerDriver` around it by hand.

Four things about it are easy to break:

- **It is a local npm package, not a loose `.js` file.** `new Worker(new URL(…))` has to resolve at
  bundle time, and a bare specifier out of `node_modules` is the only shape that works from a
  library module. Both `jsMain` and `wasmJsMain` declare it.
- **The `exec` response must stay `res[0] ?? { values: [] }`.** `db.exec` also returns `[]` for a
  `SELECT` that matched nothing, so returning anything richer — a rows-modified count, say — makes
  a cache miss look like a row to SQLDelight's cursor.
- **The database name travels as the worker's own name** (`new Worker(url, { name })`), because
  SQLDelight's message protocol has no field for it. It keys the IndexedDB snapshot.
- **The `Worker` must not move up into `webMain`.** `WebWorkerDriver` takes SQLDelight's
  `expect class Worker`, actualised as a typealias to `org.w3c.dom.Worker`. A typealias only
  expands in a *platform* compilation, so js and wasmJs both accept a `Worker` there while
  `compileWebMainKotlinMetadata` — which also compiles that source set — sees an opaque expect
  class and fails the argument. That is why the seam is `persistentSqlJsDriver(): SqlDriver`:
  `SqlDriver` is an ordinary common type.

That last one is the trap worth remembering: **the failure blocks `assemble` for `:network` and
everything above it, and neither web target's own compile task reproduces it.** Verify a change
here with `./gradlew :network:assemble`, not with `:network:compileKotlinJs`.

**Persisting the cache is not what makes the app work offline.** The service worker does — see the
`web-app` skill. The persistent cache only helps once the page is already running.

## The per-platform seam

Per-platform Apollo client configuration goes through
`ApolloClient.Builder.platformConfiguration()`, an `expect` extension in `network/src/commonMain`.
Everything that does not vary — the endpoint, the in-memory cache tier, the Metro provider itself —
stays in `commonMain`. **Add new per-platform concerns (HTTP engines, interceptors) to that seam
rather than forking the provider.**

`NetworkProviders` carries `@ContributesTo(AppScope::class)`, so it is picked up by graph
aggregation from wherever `:network` sits on the compile classpath. It must stay `api` on the graph
module, not `implementation` — see the Metro rules in `AGENTS.md`.

## Logging

Take Kermit as an `implementation` dependency and accept a `Logger` as a parameter; never hold one
in a file-level `private val` and never reach for it as a global. `mapToOutcome(logger, …)` is the
shape for a free function. `MappersTest` in `:repository` pins, via `kermit-test`'s `TestLogWriter`,
that failures are logged with their throwable and that cache misses and GraphQL errors are not —
that assertion is only possible because the logger is injected.

## Verifying a change here

- `./gradlew :network:assemble` — the one that catches the `webMain` metadata trap.
- `./gradlew :repository:allTests` — where the mapping tests live; `:network` itself has no tests.
- If the web worker changed, run the browser app and confirm the IndexedDB snapshot survives a
  reload: `./gradlew :web:wasmJsBrowserDevelopmentRun`.
