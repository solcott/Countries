package io.github.solcott.countries.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.errorOrNull
import io.github.solcott.countries.presenter.isLoading
import io.github.solcott.countries.presenter.isNotFound

/** Shown where the API has no value for a field. */
private const val ABSENT = "—"

@CircuitInject(CountryDetailScreen::class, AppScope::class)
@Composable
fun CountryDetailUi(
  state: CountryDetailScreen.State,
  screen: CountryDetailScreen,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      CountriesTopAppBar(
        navigationIcon = {
          IconButton({ state.eventSink(CountryDetailScreen.Event.BackClicked) }) {
            Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Back")
          }
        }
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      val content = state.content
      val country = content.data
      val error = content.errorOrNull
      when {
        country == null && content.isLoading -> CircularProgressIndicator()
        country == null && error != null ->
          ErrorContent(
            message = error.toUserMessage(),
            onRetry = { state.eventSink(CountryDetailScreen.Event.Retry) },
          )
        content.isNotFound -> {
          Text(stringResource(R.string.country_with_code_not_found, screen.code))
        }
        country != null -> CountryDetailContent(country, Modifier.fillMaxSize())
      }
    }
  }
}

@Composable
private fun CountryDetailContent(country: CountryDetail, modifier: Modifier = Modifier) {
  Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
    Text(country.emoji, fontSize = 56.sp)
    Text(
      country.name,
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(top = 8.dp),
    )
    Text(country.nativeName, style = MaterialTheme.typography.bodyLarge)

    Column(modifier = Modifier.padding(top = 24.dp)) {
      DetailRow(stringResource(R.string.capital, country.capital ?: ABSENT))
      DetailRow(stringResource(R.string.continent, country.continentName))
      DetailRow(stringResource(R.string.currency, country.currency ?: ABSENT))
      DetailRow(stringResource(R.string.calling_code, country.phone))
      DetailRow(stringResource(R.string.languages, country.languages.joinToString { it.name }))
    }
  }
}

@Composable
private fun DetailRow(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}
