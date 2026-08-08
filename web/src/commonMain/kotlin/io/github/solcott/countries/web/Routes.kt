package io.github.solcott.countries.web

import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen

/**
 * The URL scheme, as hash routes.
 *
 * | Screen                | URL                |
 * |-----------------------|--------------------|
 * | [CountryListScreen]   | `#/`               |
 * | [CountryDetailScreen] | `#/country/{code}` |
 *
 * Hash routing rather than the History API's path routing because a static bundle has no server to
 * rewrite `/country/FR` back to `index.html`; a hash never reaches the server at all.
 */
internal const val LIST_ROUTE = "#/"

private const val DETAIL_PREFIX = "#/country/"

/** `null` for a screen that has no URL of its own, which is not a case this app has yet. */
internal fun Screen.toRoute(): String? =
  when (this) {
    is CountryListScreen -> LIST_ROUTE
    // Country codes are ISO 3166-1 alpha-2, so there is nothing here to escape.
    is CountryDetailScreen -> DETAIL_PREFIX + code
    else -> null
  }

/** `null` for anything unrecognised — a hand-edited URL, or a route from a future version. */
internal fun String.toScreen(): Screen? =
  when {
    this == "" || this == "#" || this == LIST_ROUTE -> CountryListScreen
    startsWith(DETAIL_PREFIX) ->
      removePrefix(DETAIL_PREFIX).takeIf(String::isNotEmpty)?.let(::CountryDetailScreen)
    else -> null
  }

/**
 * The backstack a URL should open with, root-first: a deep link to a country puts the list
 * underneath it, so browser back from a shared link goes somewhere useful rather than out of the
 * app.
 */
internal fun String.toInitialScreens(): List<Screen> =
  when (val screen = toScreen()) {
    null,
    CountryListScreen -> listOf(CountryListScreen)
    else -> listOf(CountryListScreen, screen)
  }
