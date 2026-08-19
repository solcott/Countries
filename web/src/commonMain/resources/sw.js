// Service worker: what makes the app load at all with no network.
//
// Persisting the Apollo cache (see :network's IndexedDB-backed SQL.js worker) only helps once the
// page is running. Without this file, an offline reload never gets that far — the bundle, the
// wasm, and the Compose resources all fail to fetch and the tab shows the browser's error page.
//
// Bump CACHE_VERSION to evict everything; the activate handler deletes any cache that is not the
// current one.
const CACHE_VERSION = 'v2';
const SHELL_CACHE = `countries-shell-${CACHE_VERSION}`;
const FONT_CACHE = `countries-fonts-${CACHE_VERSION}`;
const API_CACHE = `countries-api-${CACHE_VERSION}`;
const CURRENT_CACHES = [SHELL_CACHE, FONT_CACHE, API_CACHE];

const GRAPHQL_ENDPOINT = 'https://countries.trevorblades.com/';
const FONT_HOST = 'fonts.gstatic.com';

self.addEventListener('install', () => {
    // Nothing is precached: the bundle filenames are content-hashed, so a hard-coded manifest
    // would rot on every build. The shell is cached as it is first requested instead, which costs
    // one online visit before the app works offline.
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches
            .keys()
            .then((keys) => Promise.all(keys.filter((k) => !CURRENT_CACHES.includes(k)).map((k) => caches.delete(k))))
            .then(() => self.clients.claim())
    );
});

/** Serve from cache immediately, refresh in the background. */
async function staleWhileRevalidate(request, cacheName) {
    const cache = await caches.open(cacheName);
    const cached = await cache.match(request);
    const network = fetch(request)
        .then((response) => {
            if (response && response.ok) cache.put(request, response.clone());
            return response;
        })
        .catch(() => null);
    return cached ?? (await network) ?? Response.error();
}

/** Immutable once fetched — never revalidate. */
async function cacheFirst(request, cacheName) {
    const cache = await caches.open(cacheName);
    const cached = await cache.match(request);
    if (cached) return cached;
    const response = await fetch(request);
    if (response && response.ok) cache.put(request, response.clone());
    return response;
}

// The Cache API refuses to key on a POST, so GraphQL requests are stored under a synthetic GET URL
// built from the query body. Same query, same key.
async function graphqlCacheKey(request) {
    const body = await request.clone().text();
    let hash = 0;
    for (let i = 0; i < body.length; i++) {
        hash = (Math.imul(31, hash) + body.charCodeAt(i)) | 0;
    }
    return new Request(`${request.url}__sw_query__${hash}`, { method: 'GET' });
}

/** Fresh data whenever the network allows it, the last known answer when it does not. */
async function networkFirstGraphql(request) {
    const cache = await caches.open(API_CACHE);
    const key = await graphqlCacheKey(request);
    try {
        const response = await fetch(request.clone());
        if (response && response.ok) cache.put(key, response.clone());
        return response;
    } catch (e) {
        const cached = await cache.match(key);
        if (cached) return cached;
        throw e;
    }
}

self.addEventListener('fetch', (event) => {
    const request = event.request;
    const url = new URL(request.url);

    if (request.method === 'POST' && request.url.startsWith(GRAPHQL_ENDPOINT)) {
        event.respondWith(networkFirstGraphql(request));
        return;
    }

    if (request.method !== 'GET') return;

    // Noto subsets fetched by Compose Multiplatform's fallback-font downloader. Caching these is
    // what keeps flags and non-Latin names from turning back into tofu offline.
    if (url.host === FONT_HOST) {
        event.respondWith(cacheFirst(request, FONT_CACHE));
        return;
    }

    // Same-origin only: everything the app is built from, including the hashed .wasm chunks and
    // the Compose resources. Skips the dev server's hot-reload endpoints, which must not be cached.
    if (url.origin === self.location.origin && !url.pathname.startsWith('/ws')) {
        event.respondWith(staleWhileRevalidate(request, SHELL_CACHE));
    }
});
