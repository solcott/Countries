package io.github.solcott.countries.network

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

internal actual fun persistentSqlJsDriver(): SqlDriver = WebWorkerDriver(persistentSqlJsWorker())

/**
 * The `new URL(…, import.meta.url)` form is what lets the bundler find the worker and emit it as
 * its own chunk; a computed path would build cleanly and 404 at runtime.
 *
 * The `js()` argument has to be a string literal, so the worker name is spelled out rather than
 * referencing [DATABASE_NAME] — the worker reads it back as `self.name` and uses it to key the
 * IndexedDB snapshot, so the two must stay in step. Kept byte-identical to the wasmJs twin.
 */
private fun persistentSqlJsWorker(): Worker =
  js(
    """new Worker(new URL("countries-sqljs-idb-worker/worker.js", import.meta.url), { name: "countries.db" })"""
  )
