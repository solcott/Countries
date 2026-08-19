package io.github.solcott.countries.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How the detail screen tells the one shared app bar what to call itself.
 *
 * `CountriesApp` owns the bar but can only see the back stack, which carries a country *code*; the
 * name lives in `CountryDetailScreen.State`, inside the detail's own Circuit. Circuit builds a `Ui`
 * from `(state, modifier)`, so there is no parameter to thread a callback through — a composition
 * local is the only channel between the two.
 *
 * That makes this the second local in `:ui`, which AGENTS.md says to resist. It earns its place the
 * way [LocalFlagFontFamily] does: the alternative considered was putting the name on
 * `CountryDetailScreen` itself, and that quietly breaks `:web` — `Routes.toScreen()` rebuilds the
 * screen from a URL with no name to give it, so a deep-linked screen would compare unequal to the
 * identical one built by a click, and `BrowserHistory` would pop and re-push on every popstate.
 *
 * **Reading it is not enough to show a title.** Nothing clears this on the way out, deliberately —
 * clearing on dispose races the swap from one country to the next, which in a two-pane window is a
 * single recomposition. `CountriesAppScaffold` decides when the value is allowed on screen: only
 * with a detail on the back stack, and only in the stacked layout. So a name left behind here is
 * unreachable, and the detail can keep publishing in two-pane mode — which is what makes the title
 * already correct the moment a window is dragged narrow across the breakpoint.
 */
internal val LocalAppBarTitle = staticCompositionLocalOf { mutableStateOf<String?>(null) }

/**
 * Publishes [title] to the shared app bar. Null while the country is still loading, which leaves
 * the bar on its default rather than flashing a placeholder.
 */
@Composable
internal fun ProvideAppBarTitle(title: String?) {
  val holder: MutableState<String?> = LocalAppBarTitle.current
  SideEffect { holder.value = title }
}
