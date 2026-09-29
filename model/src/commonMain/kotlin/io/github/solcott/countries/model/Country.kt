package io.github.solcott.countries.model

import androidx.compose.runtime.Immutable

/** Summary of a country, as shown in the list screen. */
@Immutable
data class Country(
  val code: String,
  val name: String,
  val emoji: String,
  val capital: String?,
  val continentName: String,
)

/** Full detail for a single country, as shown in the detail screen. */
@Immutable
data class CountryDetail(
  val code: String,
  val name: String,
  val nativeName: String,
  val emoji: String,
  val capital: String?,
  val currency: String?,
  val phone: String,
  val continentName: String,
  val languages: List<Language>,
)

@Immutable data class Language(val code: String, val name: String)
