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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.presenter.ContinentsState
import io.github.solcott.countries.presenter.CountriesState
import io.github.solcott.countries.presenter.CountryListScreen

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(CountryListScreen::class, AppScope::class)
@Composable
fun CountryListUi(state: CountryListScreen.State, modifier: Modifier = Modifier) {
  Scaffold(
      modifier = modifier,
      topBar = {
        CountriesTopAppBar()
      },
  ) { padding ->
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
      val countriesState = state.countriesState
      when {
        countriesState.loading -> CircularProgressIndicator()
        countriesState.error ->
            ErrorContent(
                message =
                    countriesState.errorMessage ?: stringResource(R.string.unknown_error_occurred),
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
    countriesState: CountriesState,
    modifier: Modifier = Modifier,
) {
  LazyColumn(modifier = modifier.imePadding()) {
    stickyHeader {
      SearchAndFilterHeader(
          state.continentsState,
          state.nameStartsWithText,
          state.selectedContinents,
          onToggleContinentSelection = { continent ->
            state.eventSink(CountryListScreen.Event.ToggleContinentSelection(continent))
          },
      )
    }
    val countries = countriesState.data
    if (countries.isNotEmpty()) {
      items(countries, key = Country::code, contentType = { "country" }) { country ->
        CountryRow(
            country = country,
            onClick = {
              state.eventSink(CountryListScreen.Event.CountryClicked(country.code))
            },
        )
        HorizontalDivider()
      }
    } else {
      item(key = "empty", "empty") {
        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
          Text(
              stringResource(R.string.no_countries_found),
          )
        }
      }
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchAndFilterHeader(
    continentsState: ContinentsState,
    nameStartsWithText: TextFieldState,
    selectedContinents: List<Continent>,
    onToggleContinentSelection: (Continent) -> Unit,
) {
  var continentDropdownExpanded by remember { mutableStateOf(false) }
  Row(
      Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .padding(vertical = 8.dp, horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    TextField(
        nameStartsWithText,
        placeholder = { Text(stringResource(R.string.search_by_name)) },
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
        IconButton(
            onClick = { continentDropdownExpanded = !continentDropdownExpanded },
        ) {
          Icon(
              painterResource(R.drawable.filter_list_24px),
              contentDescription = stringResource(R.string.filter),
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
                    Icon(painterResource(R.drawable.check_small_24px), "Checked")
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
