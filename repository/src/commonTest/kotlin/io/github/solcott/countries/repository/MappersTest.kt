package io.github.solcott.countries.repository

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Error as GraphQLError
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.exception.ApolloOfflineException
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.exception.DefaultApolloException
import com.apollographql.apollo.exception.HttpCacheMissException
import com.apollographql.apollo.exception.JsonDataException
import com.apollographql.apollo.exception.JsonEncodingException
import com.apollographql.cache.normalized.CacheInfo
import com.benasher44.uuid.uuid4
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.DataError
import io.github.solcott.countries.model.Language
import io.github.solcott.countries.model.Origin
import io.github.solcott.countries.model.Outcome
import io.github.solcott.countries.network.graphql.CountriesQuery
import io.github.solcott.countries.network.graphql.CountryDetailQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/**
 * Covers `Mappers.kt` — the only real logic in this module: how an [ApolloResponse] is classified
 * into an [Outcome], and how Apollo's exception hierarchy collapses into [DataError].
 */
@OptIn(ExperimentalKermitApi::class)
class MappersTest {

  private val logWriter = TestLogWriter(loggable = Severity.Verbose)
  private val logger = Logger(TestConfig(Severity.Verbose, listOf(logWriter)), tag = "TestTag")

  private suspend fun ApolloResponse<CountriesQuery.Data>.outcomes(): List<Outcome<List<Country>>> =
    flowOf(this).mapToOutcome(logger) { countries.map { it.toModel() } }.toList()

  // --- Origin tagging -------------------------------------------------------------------------

  @Test
  fun dataIsTaggedNetworkWhenNotFromCache() = runTest {
    val outcomes = countriesResponse(data = germanyData).outcomes()

    assertEquals(listOf(Outcome.Data(listOf(germanyModel), Origin.Network)), outcomes)
  }

  @Test
  fun dataIsTaggedCacheWhenFromCache() = runTest {
    val outcomes = countriesResponse(data = germanyData, fromCache = true).outcomes()

    assertEquals(listOf(Outcome.Data(listOf(germanyModel), Origin.Cache)), outcomes)
  }

  // --- Error classification -------------------------------------------------------------------

  @Test
  fun graphQlErrorsMapToApiError() = runTest {
    val response =
      countriesResponse(
        data = germanyData,
        errors = listOf(GraphQLError.Builder("boom").build(), GraphQLError.Builder("bang").build()),
      )

    assertEquals(
      listOf(Outcome.Error(DataError.Api(listOf("boom", "bang")), Origin.Network)),
      response.outcomes(),
    )
  }

  @Test
  fun graphQlErrorsKeepTheCacheOrigin() = runTest {
    val response =
      countriesResponse(errors = listOf(GraphQLError.Builder("boom").build()), fromCache = true)

    assertEquals(
      listOf(Outcome.Error(DataError.Api(listOf("boom")), Origin.Cache)),
      response.outcomes(),
    )
  }

  @Test
  fun networkExceptionMapsToNetworkError() = runTest {
    val outcomes = countriesResponse(exception = ApolloNetworkException("offline")).outcomes()

    assertEquals(listOf(Outcome.Error(DataError.Network, Origin.Network)), outcomes)
  }

  @Test
  fun offlineExceptionMapsToNetworkError() = runTest {
    val outcomes = countriesResponse(exception = ApolloOfflineException()).outcomes()

    assertEquals(listOf(Outcome.Error(DataError.Network, Origin.Network)), outcomes)
  }

  @Test
  fun httpExceptionCarriesTheStatusCode() = runTest {
    val exception =
      ApolloHttpException(statusCode = 503, headers = emptyList(), body = null, message = "nope")

    assertEquals(
      listOf(Outcome.Error(DataError.Http(503), Origin.Network)),
      countriesResponse(exception = exception).outcomes(),
    )
  }

  @Test
  fun jsonExceptionsMapToSerializationError() = runTest {
    assertEquals(
      listOf(Outcome.Error(DataError.Serialization, Origin.Network)),
      countriesResponse(exception = JsonDataException("bad shape")).outcomes(),
    )
    assertEquals(
      listOf(Outcome.Error(DataError.Serialization, Origin.Network)),
      countriesResponse(exception = JsonEncodingException("bad json")).outcomes(),
    )
  }

  @Test
  fun unclassifiedExceptionRetainsCauseAndMessage() = runTest {
    val exception = DefaultApolloException("something else entirely")

    val outcomes = countriesResponse(exception = exception).outcomes()

    assertEquals(
      listOf(
        Outcome.Error(
          DataError.Unknown(cause = exception, message = "something else entirely"),
          Origin.Network,
        )
      ),
      outcomes,
    )
  }

  // --- Cache misses are dropped, not surfaced -------------------------------------------------

  @Test
  fun cacheMissIsDropped() = runTest {
    val exception = CacheMissException(key = "Country:DE", fieldName = "name")

    assertTrue(countriesResponse(exception = exception).outcomes().isEmpty())
  }

  @Test
  fun httpCacheMissIsDropped() = runTest {
    val exception = HttpCacheMissException("not cached")

    assertTrue(countriesResponse(exception = exception).outcomes().isEmpty())
  }

  @Test
  fun errorsWinOverACacheMissException() = runTest {
    // hasErrors() is checked before the cache-miss drop, so this must surface rather than vanish.
    val response =
      countriesResponse(
        errors = listOf(GraphQLError.Builder("boom").build()),
        exception = CacheMissException(key = "Country:DE", fieldName = "name"),
      )

    assertEquals(
      listOf(Outcome.Error(DataError.Api(listOf("boom")), Origin.Network)),
      response.outcomes(),
    )
  }

  // --- What actually reaches the logger -------------------------------------------------------

  @Test
  fun unclassifiedExceptionIsLoggedWithItsThrowable() = runTest {
    val exception = DefaultApolloException("something else entirely")

    countriesResponse(exception = exception).outcomes()

    logWriter.assertCount(1)
    val entry = logWriter.logs.single()
    assertEquals(Severity.Error, entry.severity)
    assertEquals("Data request failed", entry.message)
    assertEquals(exception, entry.throwable)
    // The tag comes from the injected logger, not from a hard-coded constant in Mappers.kt.
    assertEquals("TestTag", entry.tag)
  }

  @Test
  fun successIsNotLogged() = runTest {
    countriesResponse(data = germanyData).outcomes()

    logWriter.assertCount(0)
  }

  @Test
  fun cacheMissIsNotLogged() = runTest {
    // Cache misses are an expected part of a cache-then-network policy, not a failure worth
    // reporting — they are dropped before the logging branch.
    countriesResponse(exception = CacheMissException(key = "Country:DE", fieldName = "name"))
      .outcomes()

    logWriter.assertCount(0)
  }

  @Test
  fun graphQlErrorsAreNotLogged() = runTest {
    // Only the exception branch logs; API-level errors are surfaced as DataError.Api instead.
    countriesResponse(errors = listOf(GraphQLError.Builder("boom").build())).outcomes()

    logWriter.assertCount(0)
  }

  // --- Generated type -> domain type ----------------------------------------------------------

  @Test
  fun countrySummaryFlattensTheContinentName() {
    assertEquals(germanyModel, germany.toModel())
  }

  @Test
  fun countryDetailMapsNativeNameAndLanguages() {
    val detail =
      CountryDetailQuery.Country(
        __typename = "Country",
        code = "DE",
        name = "Germany",
        native = "Deutschland",
        emoji = "🇩🇪",
        capital = "Berlin",
        currency = "EUR",
        phone = "49",
        continent = CountryDetailQuery.Continent("Continent", code = "EU", name = "Europe"),
        languages = listOf(CountryDetailQuery.Language("Language", code = "de", name = "German")),
      )

    assertEquals(
      CountryDetail(
        code = "DE",
        name = "Germany",
        nativeName = "Deutschland",
        emoji = "🇩🇪",
        capital = "Berlin",
        currency = "EUR",
        phone = "49",
        continentName = "Europe",
        languages = listOf(Language(code = "de", name = "German")),
      ),
      detail.toModel(),
    )
  }
}

private val germany =
  CountriesQuery.Country(
    __typename = "Country",
    code = "DE",
    name = "Germany",
    emoji = "🇩🇪",
    capital = "Berlin",
    continent = CountriesQuery.Continent("Continent", code = "EU", name = "Europe"),
  )

private val germanyData = CountriesQuery.Data(countries = listOf(germany))

private val germanyModel =
  Country(
    code = "DE",
    name = "Germany",
    emoji = "🇩🇪",
    capital = "Berlin",
    continentName = "Europe",
  )

/**
 * Builds a response the way Apollo would. [CacheInfo] is what `isFromCache` reads, so setting it
 * here is enough to exercise [Origin] tagging without a real normalized cache.
 */
private fun countriesResponse(
  data: CountriesQuery.Data? = null,
  errors: List<GraphQLError>? = null,
  exception: ApolloException? = null,
  fromCache: Boolean = false,
): ApolloResponse<CountriesQuery.Data> =
  ApolloResponse.Builder(CountriesQuery(), uuid4())
    .data(data)
    .errors(errors)
    .exception(exception)
    .addExecutionContext(CacheInfo.Builder().fromCache(fromCache).build())
    .build()
