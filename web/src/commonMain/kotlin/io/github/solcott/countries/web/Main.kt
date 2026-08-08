package io.github.solcott.countries.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.slack.circuit.backstack.rememberSaveableBackStack
import dev.zacsweers.metro.createGraph
import io.github.solcott.countries.shared.compose.ComposeGraph
import io.github.solcott.countries.ui.CountriesApp
import kotlinx.browser.window

/**
 * The browser entry point, and the js/wasmJs counterpart to `:app`'s `MainActivity`. Both do the
 * same two things: read the `Circuit` out of [ComposeGraph], and hand it to `CountriesApp`.
 *
 * [ComposeViewport] handles waiting for the DOM and, on wasm, for the runtime — no `onWasmReady`
 * wrapper is needed around this.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  // Caches the shell so the app loads offline. Fire-and-forget — nothing below waits on it.
  registerServiceWorker()

  val circuit = createGraph<ComposeGraph>().circuit

  ComposeViewport(viewportContainerId = VIEWPORT_ID) {
    // Seeded from the URL so a shared `#/country/FR` link opens on the detail screen. The
    // backstack is hoisted out of CountriesApp because BrowserHistory binds it to window.history.
    val backStack = rememberSaveableBackStack(window.location.hash.toInitialScreens())
    BrowserHistory(backStack)
    // onRootPop stays the default no-op: there is no browser tab to close.
    CountriesApp(circuit = circuit, backStack = backStack)
  }
}

/** Matches the container in `index.html`. */
private const val VIEWPORT_ID = "composeApp"
