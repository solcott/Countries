package io.github.solcott.countries.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.screen.PopResult
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.ui.theme.AppTheme

/**
 * The whole app, from the theme down: everything a Compose platform entry point needs beyond
 * building the Metro graph. The Android Activity, the browser `main()`, and the iOS and desktop
 * entry points to come all call this rather than re-deriving the Circuit wiring.
 *
 * It owns the app's only [Scaffold] and its only app bar. Both screens are pane content — see
 * [ListDetailNavDecoration], which puts them side by side on a window wide enough for two.
 *
 * [backStack] is hoisted because the browser app binds it to `window.history` — see `:web`. Callers
 * that do not need a handle on it can let it default.
 *
 * [onRootPop] has no default in Circuit's common `rememberCircuitNavigator`; only the Android-only
 * overload supplies one. It stays explicit here because what "pop past the root" means is genuinely
 * per-platform: Android finishes the Activity, a browser tab has nothing to close.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CountriesApp(
  circuit: Circuit,
  modifier: Modifier = Modifier,
  backStack: SaveableBackStack = rememberSaveableBackStack(root = CountryListScreen),
  onRootPop: (PopResult?) -> Unit = {},
) {
  AppTheme {
    // `enableBackHandler` is passed explicitly because leaving it off does not mean "default to
    // true" — it selects a *different* overload, the two-argument one, which installs no
    // NavigationBackHandler at all. Without it Android's system back never reaches the navigator,
    // so backing out of the detail screen exited the app instead of returning to the list.
    val navigator = rememberCircuitNavigator(backStack, onRootPop, enableBackHandler = true)
    CircuitCompositionLocals(circuit) {
      CountriesAppScaffold(
        navigator = navigator,
        backStack = backStack,
        // Two panes from 600dp rather than Material's own 840dp, which is what the ...OnMediumWidth
        // variant buys. That matches the SwiftUI app, where NavigationSplitView shows both columns
        // on iPad mini portrait (744pt) and iPad Air portrait (834pt) — 840dp would leave those
        // device shapes single-pane on Compose while iOS splits them.
        directive =
          countriesPaneDirective(
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfoV2())
          ),
        modifier = modifier.fillMaxSize(),
      )
    }
  }
}

/**
 * [CountriesApp] minus the graph wiring, so previews can force a [directive] rather than hope the
 * preview renderer reports the device width the preview asked for.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun CountriesAppScaffold(
  navigator: Navigator,
  backStack: SaveableBackStack,
  directive: PaneScaffoldDirective,
  modifier: Modifier = Modifier,
) {
  val twoPane = directive.maxHorizontalPartitions > 1
  Scaffold(
    modifier = modifier,
    topBar = {
      CountriesTopAppBar(
        // Nothing to go back to beside two live panes: closing the detail there leaves the
        // placeholder, not a previous screen.
        onBack = if (!twoPane && backStack.size > 1) ({ navigator.pop() }) else null
      )
    },
  ) { padding ->
    NavigableCircuitContent(
      navigator = navigator,
      backStack = backStack,
      decoration = remember(directive) { ListDetailNavDecoration(directive) },
      modifier = Modifier.padding(padding).fillMaxSize(),
    )
  }
}

/**
 * Renders [CountriesAppScaffold] against a preview [Circuit], for the previews below.
 *
 * Each one fixes a layout and a back stack, since those two together are the whole state space: one
 * pane or two, with a country open or not.
 */
@Composable
private fun AppPreview(
  directive: PaneScaffoldDirective,
  screens: List<Screen> = listOf(CountryListScreen),
) {
  AppTheme {
    val backStack = rememberSaveableBackStack(screens)
    CircuitCompositionLocals(previewCircuit()) {
      CountriesAppScaffold(
        navigator = rememberCircuitNavigator(backStack) {},
        backStack = backStack,
        directive = directive,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/**
 * The real thing at every size we ship to, picking its own layout — the sweep that shows the split
 * actually happening, since [AppScreenPreviews] spans phone, foldable, tablet and desktop.
 */
@AppScreenPreviews
@Composable
private fun CountriesAppPreview() {
  CountriesApp(circuit = previewCircuit())
}

/** Wide, nothing picked: the list beside [NoCountrySelected]. */
@Preview(name = "Two pane - empty", device = "spec:width=1280dp,height=800dp,dpi=160")
@Composable
private fun CountriesAppTwoPaneEmptyPreview() {
  AppPreview(twoPaneDirective)
}

/**
 * Wide with a country open. The bar has no back button here and the list keeps the selected row
 * marked — the two things that distinguish this from the stacked layout.
 */
@Preview(name = "Two pane - selected", device = "spec:width=1280dp,height=800dp,dpi=160")
@Composable
private fun CountriesAppTwoPaneSelectedPreview() {
  AppPreview(twoPaneDirective, previewDetailRoute)
}

/**
 * Narrow with a country open: the detail fills the window and the bar grows a back button. Also the
 * shape a `#/country/CH` deep link produces on a phone.
 */
@Preview(name = "Stacked - detail", device = "spec:width=411dp,height=891dp")
@Composable
private fun CountriesAppStackedDetailPreview() {
  AppPreview(singlePaneDirective, previewDetailRoute)
}
