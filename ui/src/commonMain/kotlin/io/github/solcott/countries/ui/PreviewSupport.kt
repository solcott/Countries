package io.github.solcott.countries.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.DataError
import io.github.solcott.countries.model.Language
import io.github.solcott.countries.model.Origin
import io.github.solcott.countries.presenter.ContentState
import io.github.solcott.countries.presenter.LoadStatus
import io.github.solcott.countries.ui.theme.AppTheme

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
 */
@Composable
internal fun PreviewSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  AppTheme { Surface(modifier = modifier, content = content) }
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
