package io.github.solcott.countries.repository

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.Response
import io.github.solcott.countries.network.CountriesApi
import kotlin.collections.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

interface CountryRepository {
    fun countriesAsFlow(nameStartsWith: String, continentCodes: List<String> = emptyList()): Flow<Response<List<Country>>>
    fun countryAsFlow(code: String): Flow<Response<CountryDetail?>>
}

/**
 * Thin pass-through to the network layer. Caching is Apollo's normalized cache, configured in
 * `network` — deliberately no second caching layer here. See AGENTS.md.
 */
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class CountryRepositoryImpl(private val api: CountriesApi) : CountryRepository {

  override fun countriesAsFlow(nameStartsWith:String, continentCodes: List<String>): Flow<Response<List<Country>>> =
      api.countriesAsFlow(nameStartsWith, continentCodes).mapToResponse { countries.map { country -> country.toModel() } }

  override fun countryAsFlow(code: String): Flow<Response<CountryDetail?>> = api.countryAsFlow(code).mapToResponse {
          country?.toModel()
       }

}