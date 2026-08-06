package io.github.solcott.countries.repository

import android.util.Log
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.exception.ApolloOfflineException
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.exception.HttpCacheMissException
import com.apollographql.apollo.exception.JsonDataException
import com.apollographql.apollo.exception.JsonEncodingException
import com.apollographql.cache.normalized.isFromCache
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.DataError
import io.github.solcott.countries.model.Language
import io.github.solcott.countries.model.Origin
import io.github.solcott.countries.model.Outcome
import io.github.solcott.countries.network.graphql.CountriesQuery
import io.github.solcott.countries.network.graphql.CountryDetailQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Mapping from Apollo generated types to `model` types. This file is the only place generated
 * GraphQL classes are allowed to appear alongside domain types.
 */
private const val TAG = "CountriesRepository"

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
 * Maps each Apollo response to a transport-agnostic [Outcome], tagged with the [Origin] it was
 * served from. Cache-miss responses are dropped rather than surfaced as errors: under a
 * cache-then-network policy a network response follows, and under a cache-only lookup the empty
 * result is handled upstream.
 */
internal fun <T : Operation.Data, R> Flow<ApolloResponse<T>>.mapToOutcome(
  mapSuccess: T.() -> R
): Flow<Outcome<R>> = mapNotNull { response ->
  val origin = if (response.isFromCache) Origin.Cache else Origin.Network
  val exception = response.exception
  when {
    response.hasErrors() ->
      Outcome.Error(DataError.Api(response.errors.orEmpty().map { it.message }), origin)
    exception is CacheMissException || exception is HttpCacheMissException -> null
    exception != null -> {
      Log.e(TAG, "Data request failed", exception)
      Outcome.Error(exception.toDataError(), origin)
    }
    else -> Outcome.Data(response.dataOrThrow().mapSuccess(), origin)
  }
}

/** Categorizes an [ApolloException] into the transport-agnostic [DataError] vocabulary. */
private fun ApolloException.toDataError(): DataError =
  when (this) {
    is ApolloOfflineException,
    is ApolloNetworkException -> DataError.Network
    is ApolloHttpException -> DataError.Http(statusCode)
    is JsonDataException,
    is JsonEncodingException -> DataError.Serialization
    else -> DataError.Unknown(cause = this, message = message)
  }
