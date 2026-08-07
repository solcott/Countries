package io.github.solcott.countries.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.presenter.ContentState
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.presenter.errorOrNull
import io.github.solcott.countries.presenter.isLoading
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.check_small_24px
import io.github.solcott.countries.ui.resources.filter
import io.github.solcott.countries.ui.resources.filter_list_24px
import io.github.solcott.countries.ui.resources.no_countries_found
import io.github.solcott.countries.ui.resources.search_by_name
import io.github.solcott.countries.ui.resources.updating
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(CountryListScreen::class, AppScope::class)
@Composable
fun CountryListUi(state: CountryListScreen.State, modifier: Modifier = Modifier) {
  Scaffold(
    modifier = modifier,
    topBar = { CountriesTopAppBar() },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
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
}

@Composable
private fun CountriesList(
  state: CountryListScreen.State,
  countriesState: ContentState<List<Country>>,
  modifier: Modifier = Modifier,
) {
  LazyColumn(modifier = modifier.fillMaxSize().imePadding()) {
    stickyHeader {
      SearchAndFilterHeader(
        state.continentsState,
        state.nameStartsWithText,
        state.selectedContinents,
        onToggleContinentSelection = { continent ->
          state.eventSink(CountryListScreen.Event.ToggleContinentSelection(continent))
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
          modifier = Modifier.animateItem(),
        )
        HorizontalDivider(modifier = Modifier.animateItem())
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
        .background(MaterialTheme.colorScheme.surface)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(stringResource(Res.string.updating))
    LinearProgressIndicator(modifier = Modifier.weight(1f))
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchAndFilterHeader(
  continentsState: ContentState<List<Continent>>,
  nameStartsWithText: TextFieldState,
  selectedContinents: List<Continent>,
  onToggleContinentSelection: (Continent) -> Unit,
  modifier: Modifier = Modifier,
) {
  var continentDropdownExpanded by remember { mutableStateOf(false) }
  Row(
    modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .padding(vertical = 8.dp, horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    TextField(
      nameStartsWithText,
      placeholder = { Text(stringResource(Res.string.search_by_name)) },
      shape = MaterialTheme.shapes.extraSmall,
      modifier = Modifier.weight(1f),
    )
    val continents = continentsState.data

    if (continents.isNotEmpty()) {

      ExposedDropdownMenuBox(
        continentDropdownExpanded,
        onExpandedChange = { continentDropdownExpanded = it },
        modifier = Modifier.align(Alignment.CenterVertically),
      ) {
        IconButton(onClick = { continentDropdownExpanded = !continentDropdownExpanded }) {
          Icon(
            painterResource(Res.drawable.filter_list_24px),
            contentDescription = stringResource(Res.string.filter),
          )
        }
        ExposedDropdownMenu(
          expanded = continentDropdownExpanded,
          onDismissRequest = { continentDropdownExpanded = false },
          modifier = Modifier.width(200.dp),
        ) {
          continents.forEach { continent ->
            DropdownMenuItem(
              text = { Text(continent.name) },
              onClick = {
                continentDropdownExpanded = false
                onToggleContinentSelection(continent)
              },
              trailingIcon = {
                if (selectedContinents.contains(continent)) {
                  Icon(painterResource(Res.drawable.check_small_24px), "Checked")
                }
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CountryRow(country: Country, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(country.emoji)
    Column(modifier = Modifier.weight(1f)) {
      Text(country.name)
      Text(listOfNotNull(country.capital, country.continentName).joinToString(" · "))
    }
  }
}

@Composable
private fun ListPreview(
  countriesState: ContentState<List<Country>>,
  continentsState: ContentState<List<Continent>> = loadedState(previewContinents),
  nameStartsWith: String = "",
  selectedContinents: List<Continent> = emptyList(),
) {
  PreviewSurface {
    CountryListUi(
      CountryListScreen.State(
        nameStartsWithText = TextFieldState(nameStartsWith),
        countriesState = countriesState,
        continentsState = continentsState,
        selectedContinents = selectedContinents,
        eventSink = {},
      )
    )
  }
}

/**
 * The loaded list at every size we ship to. Rows spanning a 1920dp desktop window is the case the
 * adaptive follow-up is meant to fix — this preview is what shows it.
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
    CountriesList(
      state =
        CountryListScreen.State(
          nameStartsWithText = TextFieldState(),
          countriesState = loadedState(previewCountries),
          continentsState = loadedState(previewContinents),
          selectedContinents = emptyList(),
          eventSink = {},
        ),
      countriesState = loadedState(previewCountries),
    )
  }
}

@ComponentWidthPreviews
@Composable
private fun SearchAndFilterHeaderPreview() {
  PreviewSurface {
    SearchAndFilterHeader(
      continentsState = loadedState(previewContinents),
      nameStartsWithText = TextFieldState("Fra"),
      selectedContinents = previewContinents.take(1),
      onToggleContinentSelection = {},
    )
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

/**
 * The row that breaks first: a name long enough to wrap and no capital to pair the continent with.
 */
@PreviewLightDark
@Composable
private fun CountryRowLongNamePreview() {
  PreviewSurface { CountryRow(country = previewCountries.last(), onClick = {}) }
}
