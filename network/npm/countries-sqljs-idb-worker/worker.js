// A SQLDelight web worker backed by SQL.js, whose database is persisted to IndexedDB.
//
// This exists because @cashapp/sqldelight-sqljs-worker — the reference worker, and what
// createDefaultWebWorkerDriver() uses — does `new SQL.Database()` and never writes it anywhere.
// SQL.js has no storage layer of its own: the database is a block of memory you are responsible
// for saving. So on that worker the SQLite tier of the Apollo cache is a second in-memory cache
// behind the first one, and everything is forgotten on reload.
//
// The message protocol is SQLDelight's and must match app.cash.sqldelight:web-worker-driver:
//   in   { id, action: "exec"|"begin_transaction"|"end_transaction"|"rollback_transaction",
//          sql, params }
//   out  { id, results } | { id, error }
// The driver reads `results.values` as rows-of-columns, and derives its rowCount from
// `results.values[0][0]`.

import initSqlJs from 'sql.js';

const IDB_NAME = 'countries-apollo-cache';
const IDB_STORE = 'sqlite';
// How long to wait for further writes before serialising the whole database. Apollo merges records
// inside a transaction, so in practice this coalesces one response's worth of writes into one save.
const FLUSH_DELAY_MS = 250;

let db = null;
let idb = null;
let flushTimer = null;

function openIdb() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(IDB_NAME, 1);
        request.onupgradeneeded = () => request.result.createObjectStore(IDB_STORE);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

function readSnapshot(connection, key) {
    return new Promise((resolve, reject) => {
        const request = connection.transaction(IDB_STORE, 'readonly').objectStore(IDB_STORE).get(key);
        request.onsuccess = () => resolve(request.result ?? null);
        request.onerror = () => reject(request.error);
    });
}

function writeSnapshot(connection, key, bytes) {
    return new Promise((resolve, reject) => {
        const tx = connection.transaction(IDB_STORE, 'readwrite');
        tx.objectStore(IDB_STORE).put(bytes, key);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
    });
}

// SQLDelight's protocol carries no database name, so it arrives as the worker's own name — set by
// `new Worker(url, { name })` on the Kotlin side — which lets one origin hold more than one.
const snapshotKey = self.name || 'default';

async function createDatabase() {
    const SQL = await initSqlJs({ locateFile: () => '/sql-wasm.wasm' });
    // A failure to reach IndexedDB (private browsing, disabled storage) must not take the cache
    // down with it — fall back to memory, which is exactly the reference worker's behaviour.
    try {
        idb = await openIdb();
        const saved = await readSnapshot(idb, snapshotKey);
        db = saved ? new SQL.Database(new Uint8Array(saved)) : new SQL.Database();
    } catch (e) {
        console.warn('sqljs-idb-worker: IndexedDB unavailable, cache will not persist', e);
        idb = null;
        db = new SQL.Database();
    }
}

function flush() {
    flushTimer = null;
    if (!db || !idb) return;
    writeSnapshot(idb, snapshotKey, db.export()).catch((e) =>
        console.warn('sqljs-idb-worker: failed to persist the database', e)
    );
}

function scheduleFlush() {
    if (flushTimer !== null) clearTimeout(flushTimer);
    flushTimer = setTimeout(flush, FLUSH_DELAY_MS);
}

// Anything that is not a plain read may have changed the database. Over-scheduling is harmless
// because the flush is debounced; missing a schema creation would not be.
function mayHaveWritten(sql) {
    return !/^\s*select/i.test(sql);
}

function onModuleReady() {
    const data = this.data;

    switch (data && data.action) {
        case 'exec': {
            if (!data['sql']) {
                throw new Error('exec: Missing query string');
            }
            // `[0] ?? { values: [] }` is the reference worker's shape and is load-bearing: db.exec
            // also returns [] for a SELECT that matched nothing, so anything richer here (a rows-
            // modified count, say) would make a cache miss look like a row to the cursor.
            const results = db.exec(data.sql, data.params)[0] ?? { values: [] };
            if (mayHaveWritten(data.sql)) scheduleFlush();
            return postMessage({ id: data.id, results });
        }

        case 'begin_transaction':
            return postMessage({ id: data.id, results: db.exec('BEGIN TRANSACTION;') });

        case 'end_transaction': {
            const results = db.exec('END TRANSACTION;');
            scheduleFlush();
            return postMessage({ id: data.id, results });
        }

        case 'rollback_transaction':
            return postMessage({ id: data.id, results: db.exec('ROLLBACK TRANSACTION;') });

        default:
            throw new Error(`Unsupported action: ${data && data.action}`);
    }
}

function onError(err) {
    return postMessage({ id: this.data.id, error: err });
}

// Deliberately not gated on `typeof importScripts === "function"` the way the reference worker is.
// That gate silently does nothing if the bundler emits a module worker, which is a failure mode
// with no error attached to it.
const ready = createDatabase();
self.onmessage = (event) => ready.then(onModuleReady.bind(event)).catch(onError.bind(event));
