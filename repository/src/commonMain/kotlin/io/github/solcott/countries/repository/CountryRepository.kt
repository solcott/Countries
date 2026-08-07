package io.github.solcott.countries.repository

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.Outcome
import io.github.solcott.countries.network.CountriesApi
import kotlinx.coroutines.flow.Flow

interface CountryRepository {
  fun countriesAsFlow(
    nameStartsWith: String,
    continentCodes: List<String> = emptyList(),
  ): Flow<Outcome<List<Country>>>

  fun countryAsFlow(code: String): Flow<Outcome<CountryDetail?>>
}

/**
 * Thin pass-through to the network layer. Caching is Apollo's normalized cache, configured in
 * `network` — deliberately no second caching layer here. See AGENTS.md.
 */
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class CountryRepositoryImpl(private val api: CountriesApi, logger: Logger) :
  CountryRepository {
  private val logger = logger.withTag("CountryRepository")

  override fun countriesAsFlow(
    nameStartsWith: String,
    continentCodes: List<String>,
  ): Flow<Outcome<List<Country>>> =
    api.countriesAsFlow(nameStartsWith, continentCodes).mapToOutcome(logger) {
      countries.map { country -> country.toModel() }
    }

  override fun countryAsFlow(code: String): Flow<Outcome<CountryDetail?>> =
    api.countryAsFlow(code).mapToOutcome(logger) { country?.toModel() }
}
