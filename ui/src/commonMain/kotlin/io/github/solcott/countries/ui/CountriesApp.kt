package io.github.solcott.countries.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.countries
import io.github.solcott.countries.ui.theme.AppSkin
import io.github.solcott.countries.ui.theme.AppTheme
import io.github.solcott.countries.ui.theme.MaterialSkin
import org.jetbrains.compose.resources.stringResource

/**
 * The whole app, from the theme down: everything a Compose platform entry point needs beyond
 * building the Metro graph. The Android Activity, the browser `main()`, and the iOS and desktop
 * entry points to come all call this rather than re-deriving the Circuit wiring.
 *
 * It owns the app's only [Scaffold] and its only app bar. Both screens are pane content — see
 * [ListDetailNavDecoration], which puts them side by side on a window wide enough for two.
 *
 * [backStack] is hoisted because the browser app binds it to `window.history` — see `:web`, and
 * [listCollapsed] for the same reason: `:desktop` toggles it from a keyboard shortcut, outside
 * composition. Callers that do not need a handle on either can let them default.
 *
 * [onRootPop] has no default in Circuit's common `rememberCircuitNavigator`; only the Android-only
 * overload supplies one. It stays explicit here because what "pop past the root" means is genuinely
 * per-platform: Android finishes the Activity, a browser tab has nothing to close.
 *
 * [skin] is how a platform asks for its own look — see [AppSkin]. It defaults to [MaterialSkin],
 * which is what Android wants and what every screenshot in this module's previews shows unless the
 * preview says otherwise.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CountriesApp(
  circuit: Circuit,
  modifier: Modifier = Modifier,
  skin: AppSkin = MaterialSkin,
  backStack: SaveableBackStack = rememberSaveableBackStack(root = CountryListScreen),
  listCollapsed: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) },
  onRootPop: (PopResult?) -> Unit = {},
) {
  AppTheme(skin) {
    // `enableBackHandler` is passed explicitly because leaving it off does not mean "default to
    // true" — it selects a *different* overload, the two-argument one, which installs no
    // NavigationBackHandler at all. Without it Android's system back never reaches the navigator,
    // so backing out of the detail screen exited the app instead of returning to the list.
    val navigator = rememberCircuitNavigator(backStack, onRootPop, enableBackHandler = true)
    CircuitCompositionLocals(circuit) {
      CountriesAppScaffold(
        navigator = navigator,
        backStack = backStack,
        listCollapsed = listCollapsed,
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
  listCollapsed: MutableState<Boolean> = remember { mutableStateOf(false) },
) {
  val twoPane = directive.maxHorizontalPartitions > 1
  val hasDetail = backStack.topRecord?.screen is CountryDetailScreen
  // Offered only where it does something. With nothing selected there is nothing to give the room
  // to, and hiding the list would leave NoCountrySelected telling you to choose from a list that is
  // not on screen.
  val canCollapse = twoPane && hasDetail
  val detailTitle = remember { mutableStateOf<String?>(null) }
  // The back stack, not the published name, decides whether a country title is shown at all —
  // nothing clears [LocalAppBarTitle] on the way out, so this gate is what keeps the last country's
  // name from outliving it. See the note on that local.
  //
  // `!twoPane` because the bar spans both panes: naming the country there would drop the app's own
  // name from the window entirely, and the detail pane is already headed by that name in
  // `headlineMedium`. It is the stacked layout, where the detail *is* the window, that needs the
  // bar to say what you are looking at — which is also how `NavigationSplitView` reads on iPhone
  // versus iPad.
  val showCountryTitle = !twoPane && hasDetail

  CompositionLocalProvider(LocalAppBarTitle provides detailTitle) {
    Scaffold(
      modifier = modifier,
      topBar = {
        CountriesTopAppBar(
          // Null while the country loads, which leaves the app's own name up rather than flashing
          // a placeholder for the frame or two before the data lands.
          title =
            detailTitle.value.takeIf { showCountryTitle } ?: stringResource(Res.string.countries),
          // Nothing to go back to beside two live panes: closing the detail there leaves the
          // placeholder, not a previous screen.
          onBack = if (!twoPane && backStack.size > 1) ({ navigator.pop() }) else null,
          listCollapsed = listCollapsed.value,
          onToggleList =
            if (canCollapse) ({ listCollapsed.value = !listCollapsed.value }) else null,
        )
      },
    ) { padding ->
      NavigableCircuitContent(
        navigator = navigator,
        backStack = backStack,
        decoration =
          remember(directive, listCollapsed.value) {
            ListDetailNavDecoration(directive, listCollapsed.value)
          },
        modifier = Modifier.padding(padding).fillMaxSize(),
      )
    }
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
  listCollapsed: Boolean = false,
) {
  AppTheme {
    val backStack = rememberSaveableBackStack(screens)
    CircuitCompositionLocals(previewCircuit()) {
      CountriesAppScaffold(
        navigator = rememberCircuitNavigator(backStack) {},
        backStack = backStack,
        directive = directive,
        modifier = Modifier.fillMaxSize(),
        listCollapsed = remember { mutableStateOf(listCollapsed) },
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
 * Wide with the list collapsed: the detail has the whole window and the bar's toggle points the
 * other way. The third axis, on top of one-pane-or-two and country-open-or-not.
 */
@Preview(name = "Two pane - collapsed", device = "spec:width=1280dp,height=800dp,dpi=160")
@Composable
private fun CountriesAppTwoPaneCollapsedPreview() {
  AppPreview(twoPaneDirective, previewDetailRoute, listCollapsed = true)
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
