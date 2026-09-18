// sql.js (pulled in transitively via :network's SQLDelight SQL.js worker driver) still carries
// Node fallbacks for fs/path/crypto. In this module's browser TEST bundle they are dead code, but
// webpack 5 errors on them rather than shimming unless told they are absent. The :web app sets the
// same fallback and additionally copies sql-wasm.wasm; a library test bundle needs only the
// fallback, never the wasm. See web/webpack.config.d/sqljs.js.
config.resolve = config.resolve || {};
config.resolve.fallback = {
    ...(config.resolve.fallback || {}),
    fs: false,
    path: false,
    crypto: false,
};
