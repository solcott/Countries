package io.github.solcott.countries.ui

import android.view.LayoutInflater
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.errorOrNull
import io.github.solcott.countries.presenter.isLoading
import io.github.solcott.countries.presenter.isNotFound
import io.github.solcott.countries.ui.databinding.ViewCountryDetailBinding

/**
 * Detail screen. Deliberately rendered from an XML layout through [AndroidView] rather than native
 * Compose, to exercise view interop — see AGENTS.md. This is not an oversight to be "fixed".
 */
@CircuitInject(CountryDetailScreen::class, AppScope::class)
@Composable
fun CountryDetailUi(
  state: CountryDetailScreen.State,
  screen: CountryDetailScreen,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = Modifier.fillMaxSize(),
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
      modifier = modifier.fillMaxSize().padding(innerPadding),
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
        else -> {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
              ViewCountryDetailBinding.inflate(LayoutInflater.from(context)).root
            },
            update = { root ->
              val context = root.context
              val country = checkNotNull(country)
              with(ViewCountryDetailBinding.bind(root)) {
                flag.text = country.emoji
                name.text = country.name
                nativeName.text = country.nativeName
                capital.text = context.getString(R.string.capital, country.capital ?: "—")
                continent.text = context.getString(R.string.continent, country.continentName)
                currency.text = context.getString(R.string.currency, country.currency ?: "—")
                phone.text = context.getString(R.string.calling_code, country.phone)
                languages.text =
                  context.getString(
                    R.string.languages,
                    country.languages.joinToString { language -> language.name },
                  )
              }
            },
          )
        }
      }
    }
  }
}
