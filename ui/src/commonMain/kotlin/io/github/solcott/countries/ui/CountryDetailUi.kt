package io.github.solcott.countries.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.isNotFound
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.calling_code
import io.github.solcott.countries.ui.resources.capital
import io.github.solcott.countries.ui.resources.continent
import io.github.solcott.countries.ui.resources.country_with_code_not_found
import io.github.solcott.countries.ui.resources.currency
import io.github.solcott.countries.ui.resources.languages
import io.github.solcott.countries.ui.theme.LocalAppSkin
import io.github.solcott.countries.uistate.ContentState
import io.github.solcott.countries.uistate.errorOrNull
import io.github.solcott.countries.uistate.isLoading
import org.jetbrains.compose.resources.stringResource

/** Shown where the API has no value for a field. */
private const val ABSENT = "—"

/**
 * The detail pane. No app bar and no back button of its own: `CountriesApp` owns the app's single
 * [androidx.compose.material3.Scaffold], because on a wide window this renders beside the list
 * rather than over it.
 *
 * That leaves [CountryDetailScreen.Event.BackClicked] with no sender here — see the note on the
 * event itself, which `:apple` still uses.
 */
@CircuitInject(CountryDetailScreen::class, AppScope::class)
@Composable
fun CountryDetailUi(
  state: CountryDetailScreen.State,
  screen: CountryDetailScreen,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val content = state.content
    val country = content.data
    // The one shared app bar lives up in `CountriesApp`, which can see the back stack but not this
    // state, so the name has to travel upward rather than being rendered here.
    ProvideAppBarTitle(country?.name)
    val error = content.errorOrNull
    when {
      country == null && content.isLoading -> CircularProgressIndicator()
      country == null && error != null ->
        ErrorContent(
          message = error.toUserMessage(),
          onRetry = { state.eventSink(CountryDetailScreen.Event.Retry) },
        )
      content.isNotFound -> {
        Text(stringResource(Res.string.country_with_code_not_found, screen.code))
      }
      country != null -> CountryDetailContent(country, Modifier.fillMaxSize())
    }
  }
}

@Composable
private fun CountryDetailContent(country: CountryDetail, modifier: Modifier = Modifier) {
  val skin = LocalAppSkin.current
  Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(skin.contentPadding)) {
    Text(country.emoji, fontSize = skin.detailFlagSize, fontFamily = LocalFlagFontFamily.current)
    Text(
      country.name,
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(top = 8.dp),
    )
    Text(country.nativeName, style = MaterialTheme.typography.bodyLarge)

    Column(modifier = Modifier.padding(top = 24.dp)) {
      DetailRow(stringResource(Res.string.capital, country.capital ?: ABSENT))
      DetailRow(stringResource(Res.string.continent, country.continentName))
      DetailRow(stringResource(Res.string.currency, country.currency ?: ABSENT))
      DetailRow(stringResource(Res.string.calling_code, country.phone))
      DetailRow(stringResource(Res.string.languages, country.languages.joinToString { it.name }))
    }
  }
}

@Composable
private fun DetailRow(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

private val previewScreen = CountryDetailScreen(previewCountryDetail.code)

@Composable
private fun DetailPreview(content: ContentState<CountryDetail?>) {
  PreviewSurface {
    CountryDetailUi(
      state = CountryDetailScreen.State(content = content, eventSink = {}),
      screen = previewScreen,
    )
  }
}

/**
 * The loaded pane at every size we ship to. On a wide window the real app gives this pane roughly
 * two thirds of the width, so a full-window preview overstates how much room it gets — see the
 * two-pane previews on `CountriesApp` for the shape it is actually laid out in.
 */
@AppScreenPreviews
@Composable
private fun CountryDetailUiPreview() {
  DetailPreview(loadedState(previewCountryDetail))
}

@PreviewLightDark
@Composable
private fun CountryDetailUiLoadingPreview() {
  DetailPreview(loadingState(null))
}

@PreviewLightDark
@Composable
private fun CountryDetailUiErrorPreview() {
  DetailPreview(failedState(null))
}

/** Settled with no country — a code the API does not know. */
@PreviewLightDark
@Composable
private fun CountryDetailUiNotFoundPreview() {
  DetailPreview(loadedState(null))
}

@ComponentWidthPreviews
@Composable
private fun CountryDetailContentPreview() {
  PreviewSurface { CountryDetailContent(previewCountryDetail) }
}

@ComponentWidthPreviews
@Composable
private fun DetailRowPreview() {
  PreviewSurface { DetailRow("Capital: Bern") }
}
