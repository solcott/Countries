package io.github.solcott.countries.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.presenterOf
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.runtime.ui.ui
import com.slack.circuit.subcircuit.SubCircuit
import com.slack.circuit.subcircuit.SubPresenter
import com.slack.circuit.subcircuit.SubUi
import io.github.solcott.countries.dataresult.DataError
import io.github.solcott.countries.dataresult.Origin
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.Language
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.presenter.SearchAndFilterScreen
import io.github.solcott.countries.ui.theme.AppSkin
import io.github.solcott.countries.ui.theme.AppTheme
import io.github.solcott.countries.ui.theme.MaterialSkin
import io.github.solcott.countries.uistate.ContentState
import io.github.solcott.countries.uistate.LoadStatus

/**
 * The screen-size sweep every full-screen composable gets.
 *
 * [PreviewScreenSizes] supplies phone portrait and landscape, an unfolded foldable, tablet portrait
 * and landscape, and desktop. The extra entry is a small browser window, which that set does not
 * cover and which is the tightest layout the web target has to survive.
 *
 * These come from `org.jetbrains.compose.ui:ui-tooling-preview`, whose `commonMain` package is
 * `androidx.compose.ui.tooling.preview` — the AndroidX names, in common code.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@PreviewScreenSizes
@Preview(name = "Browser - compact", device = "spec:width=800dp,height=600dp,dpi=160")
annotation class AppScreenPreviews

/**
 * Widths for a composable that is a strip inside a screen rather than a screen: a small phone, a
 * pane on a split tablet, and a full desktop window.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Compact 360dp", widthDp = 360, showBackground = true)
@Preview(name = "Medium 700dp", widthDp = 700, showBackground = true)
@Preview(name = "Expanded 1280dp", widthDp = 1280, showBackground = true)
annotation class ComponentWidthPreviews

/**
 * Wrapper every preview renders through, so previews use the real color scheme and typography
 * rather than bare Material defaults.
 *
 * [AppTheme] resolves its own `darkTheme` from `isSystemInDarkTheme()`, which the preview renderer
 * drives from the `uiMode` parameter — that is what makes `@PreviewLightDark` work without passing
 * anything through here.
 *
 * [skin] defaults to [MaterialSkin], so a preview shows the Android look unless it says otherwise.
 * A composable whose point is that it differs per platform gets one preview per skin instead.
 */
@Composable
internal fun PreviewSurface(
  modifier: Modifier = Modifier,
  skin: AppSkin = MaterialSkin,
  content: @Composable () -> Unit,
) {
  AppTheme(skin) { Surface(modifier = modifier, content = content) }
}

internal val previewCountries =
  listOf(
    Country(
      code = "FR",
      name = "France",
      emoji = "🇫🇷",
      capital = "Paris",
      continentName = "Europe",
    ),
    Country(code = "JP", name = "Japan", emoji = "🇯🇵", capital = "Tokyo", continentName = "Asia"),
    Country(
      code = "BR",
      name = "Brazil",
      emoji = "🇧🇷",
      capital = "Brasília",
      continentName = "South America",
    ),
    Country(
      code = "NZ",
      name = "New Zealand",
      emoji = "🇳🇿",
      capital = "Wellington",
      continentName = "Oceania",
    ),
    // The awkward one, deliberately: no capital, and a name long enough to wrap at 360dp. Those
    // are the two things that break a row, so every list preview carries them.
    Country(
      code = "GS",
      name = "South Georgia and the South Sandwich Islands",
      emoji = "🇬🇸",
      capital = null,
      continentName = "Antarctica",
    ),
  )

internal val previewContinents =
  listOf(
    Continent(code = "AF", name = "Africa"),
    Continent(code = "AN", name = "Antarctica"),
    Continent(code = "AS", name = "Asia"),
    Continent(code = "EU", name = "Europe"),
    Continent(code = "NA", name = "North America"),
    Continent(code = "OC", name = "Oceania"),
    Continent(code = "SA", name = "South America"),
  )

internal val previewCountryDetail =
  CountryDetail(
    code = "CH",
    name = "Switzerland",
    nativeName = "Schweiz",
    emoji = "🇨🇭",
    capital = "Bern",
    currency = "CHF",
    phone = "41",
    continentName = "Europe",
    languages =
      listOf(
        Language(code = "de", name = "German"),
        Language(code = "fr", name = "French"),
        Language(code = "it", name = "Italian"),
        Language(code = "rm", name = "Romansh"),
      ),
  )

/** Settled with data from the network — the happy path. */
internal fun <T> loadedState(data: T) = ContentState(data, Origin.Network, LoadStatus.Idle)

/** A first load with nothing to show yet. */
internal fun <T> loadingState(data: T) = ContentState(data, null, LoadStatus.Loading)

/** Cached data on screen with a network request still in flight. */
internal fun <T> refreshingState(data: T) = ContentState(data, Origin.Cache, LoadStatus.Loading)

internal fun <T> failedState(data: T, error: DataError = DataError.Network) =
  ContentState(data, null, LoadStatus.Failed(error))

/**
 * A [Circuit] wired to the real UIs but to presenters that just hand back the fixtures above, so
 * `CountriesApp` — which takes a [Circuit] and nothing else — can be previewed without a Metro
 * graph, a repository or the network.
 *
 * Navigation between these screens does work in a running app; it does not in a preview, which is
 * why the detail preview seeds its backstack with [previewDetailRoute] instead of clicking through.
 */
internal fun previewCircuit(): Circuit =
  Circuit.Builder()
    .addPresenter<CountryListScreen, CountryListScreen.State>(
      presenterOf {
        CountryListScreen.State(countriesState = loadedState(previewCountries), eventSink = {})
      }
    )
    .addUi<CountryListScreen, CountryListScreen.State> { state, modifier ->
      CountryListUi(state, modifier)
    }
    .addPresenter<CountryDetailScreen, CountryDetailScreen.State>(
      presenterOf {
        CountryDetailScreen.State(
          content = loadedState<CountryDetail?>(previewCountryDetail),
          eventSink = {},
        )
      }
    )
    // addUi passes only (state, modifier); CountryDetailUi also takes its Screen, so this one goes
    // through the factory directly.
    .addUiFactory { screen, _ ->
      if (screen is CountryDetailScreen) {
        ui<CountryDetailScreen.State> { state, modifier ->
          CountryDetailUi(state, screen, modifier)
        }
      } else {
        null
      }
    }
    .build()

/** Builds a fixed header state, so the header previews read as a table of what it can look like. */
internal fun searchAndFilterState(
  nameStartsWith: String = "",
  continentsState: ContentState<List<Continent>> = loadedState(previewContinents),
  selectedContinents: List<Continent> = emptyList(),
  continentDropdownExpanded: Boolean = false,
) =
  SearchAndFilterScreen.State(
    nameStartsWithText = TextFieldState(nameStartsWith),
    continentsState = continentsState,
    selectedContinents = selectedContinents,
    continentDropdownExpanded = continentDropdownExpanded,
    eventSink = {},
  )

/**
 * The [SubCircuit] half of [previewCircuit], for any preview that renders the country list.
 *
 * Not optional: `SubCircuitContent` reads `LocalSubCircuit`, which defaults to null and is behind a
 * `requireNotNull`. A preview that forgets it throws rather than drawing a placeholder — the
 * placeholder path is for a screen with no factory registered, not for a missing SubCircuit.
 *
 * Hand-built rather than injected, for the same reason [previewCircuit] is: a preview has no Metro
 * graph. Only the presenter is faked — the real [SearchAndFilterUi] draws it, so these previews
 * still show the header that ships.
 */
internal fun previewSubCircuit(
  nameStartsWith: String = "",
  continentsState: ContentState<List<Continent>> = loadedState(previewContinents),
  selectedContinents: List<Continent> = emptyList(),
): SubCircuit =
  SubCircuit.builder()
    .addPresenterFactory { screen ->
      if (screen is SearchAndFilterScreen) {
        object : SubPresenter<SearchAndFilterScreen.OuterEvent, SearchAndFilterScreen.State> {
          @Composable
          override fun present(
            outerEventSink: (SearchAndFilterScreen.OuterEvent) -> Unit
          ): SearchAndFilterScreen.State = remember {
            searchAndFilterState(nameStartsWith, continentsState, selectedContinents)
          }
        }
      } else {
        null
      }
    }
    .addUiFactory { screen ->
      if (screen is SearchAndFilterScreen) {
        SubUi<SearchAndFilterScreen.State> { state, modifier -> SearchAndFilterUi(state, modifier) }
      } else {
        null
      }
    }
    .build()

/** Root-first, the shape a `#/country/CH` deep link produces. */
internal val previewDetailRoute: List<Screen> =
  listOf(CountryListScreen, CountryDetailScreen(previewCountryDetail.code))

/**
 * The two layouts `CountriesAppScaffold` can be in, as directives a preview can pass it directly.
 *
 * Built by hand rather than through `calculatePaneScaffoldDirective...`, which would read the
 * window size out of `LocalWindowInfo` — a preview would then be at the mercy of what the renderer
 * reports for its `device` spec, and a two-pane preview that quietly rendered one pane would look
 * like a layout bug rather than a preview artifact. The numbers below are what that function
 * returns for each case.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal val twoPaneDirective =
  countriesPaneDirective(
    PaneScaffoldDirective(
      maxHorizontalPartitions = 2,
      horizontalPartitionSpacerSize = 24.dp,
      maxVerticalPartitions = 1,
      verticalPartitionSpacerSize = 0.dp,
      defaultPanePreferredWidth = 360.dp,
      defaultPanePreferredHeight = 420.dp,
      excludedBounds = emptyList(),
    )
  )

/** See [twoPaneDirective]. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal val singlePaneDirective =
  countriesPaneDirective(
    PaneScaffoldDirective(
      maxHorizontalPartitions = 1,
      horizontalPartitionSpacerSize = 0.dp,
      maxVerticalPartitions = 1,
      verticalPartitionSpacerSize = 0.dp,
      defaultPanePreferredWidth = 360.dp,
      defaultPanePreferredHeight = 420.dp,
      excludedBounds = emptyList(),
    )
  )
