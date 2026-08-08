// Apollo's normalized cache runs on SQLDelight's SQL.js web-worker driver here (see
// :network's platformConfiguration web actual). sql.js loads its own `.wasm` at runtime by URL, so
// webpack has to place that file next to the bundle — nothing in the Kotlin sources references it,
// which means nothing else would pull it in. Without this the app builds cleanly and then 404s on
// first cache read.
//
// One webpack.config.d at the module root serves both the js and the wasmJs webpack builds.
const CopyWebpackPlugin = require('copy-webpack-plugin');

// sql.js's npm package still carries Node fallbacks for these; in a browser build they are dead
// code, and webpack 5 errors rather than shimming them unless told they are absent.
config.resolve = config.resolve || {};
config.resolve.fallback = {
    ...(config.resolve.fallback || {}),
    fs: false,
    path: false,
    crypto: false,
};

// Relative to the webpack working directory, build/{js,wasm}/packages/<project>/.
config.plugins.push(
    new CopyWebpackPlugin({
        patterns: ['../../node_modules/sql.js/dist/sql-wasm.wasm'],
    })
);
