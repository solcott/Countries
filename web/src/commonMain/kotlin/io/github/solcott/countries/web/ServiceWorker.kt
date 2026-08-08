// navigator.serviceWorker is JS interop, which is behind the opt-in on wasmJs.
@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.countries.web

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Registers `sw.js`, which caches the app shell, the Noto fallback fonts and GraphQL responses.
 *
 * This is what makes the app load with no network at all. The persistent Apollo cache in `:network`
 * only helps once the page is already running; without a service worker an offline reload never
 * gets that far, because the bundle itself cannot be fetched.
 *
 * Registration is fire-and-forget: a browser without service workers, or a page served over plain
 * HTTP from something other than localhost, simply keeps working online. Nothing waits on this.
 */
internal fun registerServiceWorker() {
  registerServiceWorkerImpl()
}

private fun registerServiceWorkerImpl(): Unit =
  js(
    """{
      if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('sw.js').catch(function (e) {
          console.warn('Service worker registration failed; the app will not work offline', e);
        });
      }
    }"""
  )
