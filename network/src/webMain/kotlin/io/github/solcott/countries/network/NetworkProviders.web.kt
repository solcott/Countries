package io.github.solcott.countries.network

import app.cash.sqldelight.db.SqlDriver
import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory

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
  memoryCacheBackedBy(SqlNormalizedCacheFactory(persistentSqlJsDriver(), name = null))

/**
 * The seam is a [SqlDriver] rather than the worker itself because this source set is compiled
 * *twice*: once per target, and once as shared web metadata. `WebWorkerDriver` takes SQLDelight's
 * `expect class Worker`, whose actual is a typealias to `org.w3c.dom.Worker`; that typealias only
 * expands in a platform compilation, so js and wasmJs both accept a `Worker` here while
 * `compileWebMainKotlinMetadata` sees an opaque expect class and fails. `SqlDriver` is an ordinary
 * common type, so it crosses the metadata compilation unharmed.
 */
internal expect fun persistentSqlJsDriver(): SqlDriver
