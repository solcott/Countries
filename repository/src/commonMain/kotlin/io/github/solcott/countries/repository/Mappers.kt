package io.github.solcott.countries.repository

import co.touchlab.kermit.Logger
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.Language
import io.github.solcott.countries.network.graphql.CountriesQuery
import io.github.solcott.countries.network.graphql.CountryDetailQuery
import io.github.solcott.dataresult.Outcome
import io.github.solcott.dataresult.apollo.mapToOutcome as mapApolloResponseToOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Mapping from Apollo generated types to `model` types. This file is the only place generated
 * GraphQL classes are allowed to appear alongside domain types.
 */
internal fun CountriesQuery.Country.toModel() =
  Country(
    code = code,
    name = name,
    emoji = emoji,
    capital = capital,
    continentName = continent.name,
  )

internal fun CountryDetailQuery.Country.toModel() =
  CountryDetail(
    code = code,
    name = name,
    nativeName = native,
    emoji = emoji,
    capital = capital,
    currency = currency,
    phone = phone,
    continentName = continent.name,
    languages = languages.map { Language(code = it.code, name = it.name) },
  )

/**
 * `dataresult-apollo`'s mapper with this module's logging policy attached, so the three repository
 * call sites stay a single expression and there is one place that decides what a transport failure
 * logs.
 *
 * [logger] is a parameter rather than a file-level singleton so callers inject their own tagged
 * instance and tests can assert on what was logged with a `TestLogWriter`.
 */
internal fun <T : Operation.Data, R> Flow<ApolloResponse<T>>.mapToOutcome(
  logger: Logger,
  mapSuccess: T.() -> R,
): Flow<Outcome<R>> =
  mapApolloResponseToOutcome(onException = { logger.e(it) { "Data request failed" } }, mapSuccess)
