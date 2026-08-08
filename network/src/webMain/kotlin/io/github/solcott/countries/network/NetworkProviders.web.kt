// `js()` returning a DOM type is JS interop, which is behind the opt-in on wasmJs.
@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.countries.network

import app.cash.sqldelight.driver.worker.WebWorkerDriver
import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import kotlin.js.ExperimentalWasmJsInterop
import org.w3c.dom.Worker

/**
 * Backed by SQL.js in a web worker — but *not* by the reference worker that
 * `createDefaultWebWorkerDriver()` would give us.
 *
 * SQL.js has no storage of its own: the database is a block of memory, and
 * `@cashapp/sqldelight-sqljs-worker` never writes it anywhere, which leaves the SQLite tier a
 * second in-memory cache behind the first one. `npm/countries-sqljs-idb-worker` is that worker with
 * a persistence layer — it loads the database from IndexedDB at startup and writes it back,
 * debounced, after every transaction.
 *
 * `name = null` mirrors what Apollo's own web actual passes; the name is a SQLite file name on
 * platforms that have files, and here the worker owns storage.
 */
internal actual fun ApolloClient.Builder.platformConfiguration(): ApolloClient.Builder =
  memoryCacheBackedBy(
    SqlNormalizedCacheFactory(WebWorkerDriver(persistentSqlJsWorker()), name = null)
  )

/**
 * The `new URL(…, import.meta.url)` form is what lets the bundler find the worker and emit it as
 * its own chunk; a computed path would build cleanly and 404 at runtime.
 *
 * Two Kotlin/Wasm constraints shape this: a `js()` call has to be the entire body of a top-level
 * function, and its argument has to be a string literal. That is why this is not inlined above and
 * why the worker name is spelled out rather than referencing [DATABASE_NAME] — the worker reads it
 * back as `self.name` and uses it to key the IndexedDB snapshot, so the two must stay in step.
 */
private fun persistentSqlJsWorker(): Worker =
  js(
    """new Worker(new URL("countries-sqljs-idb-worker/worker.js", import.meta.url), { name: "countries.db" })"""
  )
