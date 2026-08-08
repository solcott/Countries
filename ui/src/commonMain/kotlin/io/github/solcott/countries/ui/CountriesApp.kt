package io.github.solcott.countries.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.runtime.screen.PopResult
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.ui.theme.AppTheme

/**
 * The whole app, from the theme down: everything a Compose platform entry point needs beyond
 * building the Metro graph. The Android Activity, the browser `main()`, and the iOS and desktop
 * entry points to come all call this rather than re-deriving the Circuit wiring.
 *
 * [backStack] is hoisted because the browser app binds it to `window.history` — see `:web`. Callers
 * that do not need a handle on it can let it default.
 *
 * [onRootPop] has no default in Circuit's common `rememberCircuitNavigator`; only the Android-only
 * overload supplies one. It stays explicit here because what "pop past the root" means is genuinely
 * per-platform: Android finishes the Activity, a browser tab has nothing to close.
 */
@Composable
fun CountriesApp(
  circuit: Circuit,
  modifier: Modifier = Modifier,
  backStack: SaveableBackStack = rememberSaveableBackStack(root = CountryListScreen),
  onRootPop: (PopResult?) -> Unit = {},
) {
  AppTheme {
    val navigator = rememberCircuitNavigator(backStack, onRootPop)
    CircuitCompositionLocals(circuit) {
      NavigableCircuitContent(
        navigator = navigator,
        backStack = backStack,
        modifier = modifier.fillMaxSize(),
      )
    }
  }
}

@AppScreenPreviews
@Composable
private fun CountriesAppPreview() {
  CountriesApp(circuit = previewCircuit())
}

/**
 * Reaching the detail screen means driving a navigation, which a preview cannot do — so this one
 * starts the backstack there instead. It is also the shape a `#/country/CH` deep link produces.
 */
@Preview(name = "Detail route", device = "spec:width=411dp,height=891dp")
@Composable
private fun CountriesAppDetailPreview() {
  CountriesApp(
    circuit = previewCircuit(),
    backStack = rememberSaveableBackStack(previewDetailRoute),
  )
}
