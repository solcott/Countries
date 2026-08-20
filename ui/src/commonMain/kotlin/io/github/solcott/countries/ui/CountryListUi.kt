package io.github.solcott.countries.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.subcircuit.LocalSubCircuit
import com.slack.circuit.subcircuit.SubCircuitContent
import dev.zacsweers.metro.AppScope
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.presenter.SearchAndFilterScreen
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.no_countries_found
import io.github.solcott.countries.ui.resources.updating
import io.github.solcott.countries.ui.theme.DesktopSkin
import io.github.solcott.countries.ui.theme.LocalAppSkin
import io.github.solcott.countries.ui.theme.SelectionStyle
import io.github.solcott.countries.uistate.ContentState
import io.github.solcott.countries.uistate.errorOrNull
import io.github.solcott.countries.uistate.isLoading
import org.jetbrains.compose.resources.stringResource

/**
 * The list pane. No app bar of its own: `CountriesApp` owns the app's single
 * [androidx.compose.material3.Scaffold], because on a wide window this renders beside the detail
 * rather than being replaced by it.
 */
@CircuitInject(CountryListScreen::class, AppScope::class)
@Composable
fun CountryListUi(state: CountryListScreen.State, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.fillMaxSize().background(listPaneColor()),
    contentAlignment = Alignment.Center,
  ) {
    val countriesState = state.countriesState
    val error = countriesState.errorOrNull
    when {
      countriesState.data.isEmpty() && countriesState.isLoading -> CircularProgressIndicator()
      countriesState.data.isEmpty() && error != null ->
        ErrorContent(
          message = error.toUserMessage(),
          onRetry = { state.eventSink(CountryListScreen.Event.Retry) },
        )
      else -> CountriesList(state, countriesState, Modifier.fillMaxSize())
    }
  }
}

/**
 * The list pane's own background.
 *
 * A desktop sidebar is a different surface from the content beside it; a Material list is the same
 * one. `surface` is what the pane already inherited from the `Scaffold`, so the untinted branch
 * paints exactly what was there before.
 */
@Composable
internal fun listPaneColor() =
  if (LocalAppSkin.current.sidebarTinted) MaterialTheme.colorScheme.surfaceContainer
  else MaterialTheme.colorScheme.surface

@Composable
private fun CountriesList(
  state: CountryListScreen.State,
  countriesState: ContentState<List<Country>>,
  modifier: Modifier = Modifier,
) {
  val skin = LocalAppSkin.current
  LazyColumn(modifier = modifier.fillMaxSize().imePadding()) {
    stickyHeader {
      // The header owns the filter; this pane only learns what it is set to. Everything the
      // sub-circuit has to say arrives as one FilterChanged, which the list presenter mirrors.
      SubCircuitContent(
        screen = SearchAndFilterScreen,
        outerEventSink = { event ->
          when (event) {
            is SearchAndFilterScreen.OuterEvent.FilterChanged ->
              state.eventSink(CountryListScreen.Event.FilterChanged(event.name, event.continents))
          }
        },
        modifier = Modifier.animateItem(),
      )
    }
    // Data is already on screen while a refresh runs (e.g. cache shown, network in flight) — keep
    // it visible and surface the in-flight request rather than blanking to a spinner.
    if (countriesState.isLoading) {
      item("loading_network", "loading_network") {
        RefreshingIndicator(modifier = Modifier.animateItem())
      }
    }
    val countries = countriesState.data
    if (countries.isNotEmpty()) {
      items(countries, key = Country::code, contentType = { "country" }) { country ->
        CountryRow(
          country = country,
          onClick = { state.eventSink(CountryListScreen.Event.CountryClicked(country.code)) },
          selected = country.code == state.selectedCountryCode,
          modifier = Modifier.animateItem(),
        )
        // A rule between every row is a Material list; a sidebar separates its rows with space and
        // a selection shape instead.
        if (skin.listDividers) HorizontalDivider(modifier = Modifier.animateItem())
      }
    } else {
      item(key = "empty", "empty") {
        Box(Modifier.fillParentMaxSize().animateItem(), contentAlignment = Alignment.Center) {
          Text(stringResource(Res.string.no_countries_found))
        }
      }
    }
  }
}

@Composable
private fun RefreshingIndicator(modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(listPaneColor())
        .padding(horizontal = LocalAppSkin.current.rowHorizontalPadding, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(stringResource(Res.string.updating))
    LinearProgressIndicator(modifier = Modifier.weight(1f))
  }
}

/**
 * [selected] marks the row the detail pane is currently showing. It only ever shows in the two-pane
 * layout — in the stacked one the detail covers the list — and it is what replaces the
 * `List(selection:)` highlight SwiftUI gives the Apple app for free.
 */
@Composable
private fun CountryRow(
  country: Country,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
) {
  val skin = LocalAppSkin.current
  val colors = MaterialTheme.colorScheme
  val interactionSource = remember { MutableInteractionSource() }
  val hovered by interactionSource.collectIsHoveredAsState()
  val fill =
    when {
      selected -> colors.secondaryContainer
      // Weak on purpose: this follows the pointer, so anything stronger flickers down the list.
      skin.hoverAffordances && hovered -> colors.onSurface.copy(alpha = 0.06f)
      else -> Color.Transparent
    }
  // A full-bleed highlight is drawn behind the row's own padding; an inset one is a shape floating
  // inside it, so the inset has to be applied before the background rather than after.
  val highlight =
    when (skin.selection) {
      SelectionStyle.FullBleed -> Modifier.background(fill)
      SelectionStyle.InsetRounded ->
        Modifier.padding(horizontal = skin.selectionInset, vertical = skin.selectionInset / 2)
          .background(fill, MaterialTheme.shapes.small)
    }
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(
          interactionSource = interactionSource,
          // A ripple spreading from the click point is a touch affordance. Where there is a hover
          // highlight it is both redundant and wrong, so the two are alternatives, not additions.
          indication = if (skin.hoverAffordances) null else LocalIndication.current,
          onClick = onClick,
        )
        .then(if (skin.hoverAffordances) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
        .then(highlight)
        .heightIn(min = skin.rowMinHeight)
        .padding(horizontal = skin.rowHorizontalPadding, vertical = skin.rowVerticalPadding),
    horizontalArrangement = Arrangement.spacedBy(skin.rowSpacing),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(country.emoji, fontFamily = LocalFlagFontFamily.current)
    Column(modifier = Modifier.weight(1f)) {
      Text(country.name)
      Text(
        listOfNotNull(country.capital, country.continentName).joinToString(" · "),
        style =
          if (skin.dimSupportingText) MaterialTheme.typography.bodyMedium
          else LocalTextStyle.current,
        color = if (skin.dimSupportingText) colors.onSurfaceVariant else Color.Unspecified,
      )
    }
  }
}

/**
 * Renders the list against a preview [com.slack.circuit.subcircuit.SubCircuit], which is not
 * optional: `SubCircuitContent` resolves the header through `LocalSubCircuit` and that local
 * defaults to null. The header's own fixtures therefore go to [previewSubCircuit] rather than into
 * the state below.
 */
@Composable
private fun ListPreview(
  countriesState: ContentState<List<Country>>,
  continentsState: ContentState<List<Continent>> = loadedState(previewContinents),
  nameStartsWith: String = "",
  selectedContinents: List<Continent> = emptyList(),
  selectedCountryCode: String? = null,
) {
  PreviewSurface {
    CompositionLocalProvider(
      LocalSubCircuit provides
        previewSubCircuit(nameStartsWith, continentsState, selectedContinents)
    ) {
      CountryListUi(
        CountryListScreen.State(
          countriesState = countriesState,
          selectedCountryCode = selectedCountryCode,
          eventSink = {},
        )
      )
    }
  }
}

/**
 * The loaded list at every size we ship to. On a wide window the real app gives this pane a fixed
 * 340dp beside the detail, so a full-window preview overstates its width — see the two-pane
 * previews on `CountriesApp` for the shape it is actually laid out in.
 */
@AppScreenPreviews
@Composable
private fun CountryListUiPreview() {
  ListPreview(loadedState(previewCountries))
}

/** First load: nothing cached, so the whole screen is a spinner. */
@PreviewLightDark
@Composable
private fun CountryListUiLoadingPreview() {
  ListPreview(loadingState(emptyList()))
}

/**
 * Stale-while-revalidate: cached rows stay on screen and the refresh is surfaced as a strip rather
 * than blanking the list.
 */
@PreviewLightDark
@Composable
private fun CountryListUiRefreshingPreview() {
  ListPreview(refreshingState(previewCountries))
}

/** Failed with nothing cached — the only case that replaces the list with an error. */
@PreviewLightDark
@Composable
private fun CountryListUiErrorPreview() {
  ListPreview(failedState(emptyList()))
}

/** A filter that matches nothing. The header stays; only the list body is empty. */
@PreviewLightDark
@Composable
private fun CountryListUiEmptyPreview() {
  ListPreview(loadedState(emptyList()), nameStartsWith = "Zzz")
}

/** Search text and two continents selected, so the filter icon has check marks behind it. */
@PreviewLightDark
@Composable
private fun CountryListUiFilteredPreview() {
  ListPreview(
    loadedState(previewCountries.take(2)),
    nameStartsWith = "Fra",
    selectedContinents = previewContinents.filter { it.code in setOf("EU", "AS") },
  )
}

/** Continents still loading, which is what removes the filter control entirely. */
@PreviewLightDark
@Composable
private fun CountryListUiWithoutContinentsPreview() {
  ListPreview(loadedState(previewCountries), continentsState = loadingState(emptyList()))
}

@ComponentWidthPreviews
@Composable
private fun CountriesListPreview() {
  PreviewSurface {
    CompositionLocalProvider(LocalSubCircuit provides previewSubCircuit()) {
      CountriesList(
        state =
          CountryListScreen.State(
            countriesState = loadedState(previewCountries),
            selectedCountryCode = previewCountries.first().code,
            eventSink = {},
          ),
        countriesState = loadedState(previewCountries),
      )
    }
  }
}

@ComponentWidthPreviews
@Composable
private fun RefreshingIndicatorPreview() {
  PreviewSurface { RefreshingIndicator() }
}

@ComponentWidthPreviews
@Composable
private fun CountryRowPreview() {
  PreviewSurface { CountryRow(country = previewCountries.first(), onClick = {}) }
}

/** The two-pane layout's selected row, marking which country the detail pane is showing. */
@ComponentWidthPreviews
@Composable
private fun CountryRowSelectedPreview() {
  PreviewSurface { CountryRow(country = previewCountries.first(), onClick = {}, selected = true) }
}

/** The desktop sidebar row: dense, dimmed second line, and an inset rounded selection. */
@ComponentWidthPreviews
@Composable
private fun CountryRowDesktopSkinPreview() {
  PreviewSurface(skin = DesktopSkin) {
    Column {
      CountryRow(country = previewCountries.first(), onClick = {}, selected = true)
      CountryRow(country = previewCountries[1], onClick = {})
    }
  }
}

/**
 * The same list without rules between the rows, which is the other half of reading as a sidebar.
 */
@ComponentWidthPreviews
@Composable
private fun CountriesListDesktopSkinPreview() {
  PreviewSurface(skin = DesktopSkin) {
    CompositionLocalProvider(LocalSubCircuit provides previewSubCircuit()) {
      CountriesList(
        state =
          CountryListScreen.State(
            countriesState = loadedState(previewCountries),
            selectedCountryCode = previewCountries.first().code,
            eventSink = {},
          ),
        countriesState = loadedState(previewCountries),
      )
    }
  }
}

/**
 * The row that breaks first: a name long enough to wrap and no capital to pair the continent with.
 */
@PreviewLightDark
@Composable
private fun CountryRowLongNamePreview() {
  PreviewSurface { CountryRow(country = previewCountries.last(), onClick = {}) }
}
