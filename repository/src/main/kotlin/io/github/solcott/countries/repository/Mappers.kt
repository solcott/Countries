package io.github.solcott.countries.repository

import android.util.Log
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloCompositeException
import com.apollographql.apollo.exception.ApolloGraphQLException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.exception.ApolloOfflineException
import com.apollographql.apollo.exception.ApolloParseException
import com.apollographql.apollo.exception.ApolloWebSocketClosedException
import com.apollographql.apollo.exception.ApolloWebSocketForceCloseException
import com.apollographql.apollo.exception.AutoPersistedQueriesNotSupported
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.exception.DefaultApolloException
import com.apollographql.apollo.exception.HttpCacheMissException
import com.apollographql.apollo.exception.JsonDataException
import com.apollographql.apollo.exception.JsonEncodingException
import com.apollographql.apollo.exception.MissingValueException
import com.apollographql.apollo.exception.NoDataException
import com.apollographql.apollo.exception.NullOrMissingField
import com.apollographql.apollo.exception.RouterError
import com.apollographql.apollo.exception.SubscriptionConnectionException
import com.apollographql.apollo.exception.SubscriptionOperationException
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.Language
import io.github.solcott.countries.model.Response
import io.github.solcott.countries.network.graphql.CountriesQuery
import io.github.solcott.countries.network.graphql.CountryDetailQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

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

internal fun <T : Operation.Data, R> Flow<ApolloResponse<T>>.mapToResponse(mapSuccess: T.() -> R) =
    mapNotNull { response ->
        if (response.hasErrors()) {
            // TODO come up with better approach to errors
            Response.Error(response.errors!!.first().message)
        } else {
            val exception = response.exception
            if(exception != null){
                Log.e("${this::class.simpleName}", "Error occurred", exception)
                when(exception){
                    is HttpCacheMissException, is CacheMissException -> null
                    else -> {
                        Log.e("${this::class.simpleName}", "Error occurred", exception)
                        Response.Error(exception.message ?: "An error occurred") // TODO handle better and log
                    }
                }
            }
            else {
                Response.Data(response.dataOrThrow().mapSuccess())
            }
        }
    }

