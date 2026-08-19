// `js()` returning a DOM type is JS interop, which is behind the opt-in on wasmJs.
@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.countries.network

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import kotlin.js.ExperimentalWasmJsInterop
import org.w3c.dom.Worker

internal actual fun persistentSqlJsDriver(): SqlDriver = WebWorkerDriver(persistentSqlJsWorker())

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
