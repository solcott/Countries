package io.github.solcott.countries.repository

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Error as GraphQLError
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.apollo.exception.DefaultApolloException
import com.apollographql.cache.normalized.CacheInfo
import com.benasher44.uuid.uuid4
import io.github.solcott.countries.model.Country
import io.github.solcott.countries.model.CountryDetail
import io.github.solcott.countries.model.Language
import io.github.solcott.countries.network.graphql.CountriesQuery
import io.github.solcott.countries.network.graphql.CountryDetailQuery
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/**
 * Covers what is left in `Mappers.kt` after the response classification moved to
 * `io.github.solcott:dataresult-apollo`: this module's logging policy, and the mapping from
 * generated GraphQL types to `model` types.
 *
 * How an [ApolloResponse] is classified into an [Outcome] — origin tagging, the exception taxonomy,
 * cache-miss dropping — is the library's contract and is tested there, in `ApolloOutcomeTest`.
 * Duplicating it here would only pin the same behaviour twice.
 */
@OptIn(ExperimentalKermitApi::class)
class MappersTest {

  private val logWriter = TestLogWriter(loggable = Severity.Verbose)
  private val logger = Logger(TestConfig(Severity.Verbose, listOf(logWriter)), tag = "TestTag")

  private suspend fun ApolloResponse<CountriesQuery.Data>.outcomes(): List<Outcome<List<Country>>> =
    flowOf(this).mapToOutcome(logger) { countries.map { it.toModel() } }.toList()

  // --- The local wrapper actually delegates ----------------------------------------------------

  @Test
  fun successfulResponsesAreMappedAndTagged() = runTest {
    // Not a restatement of the library's origin test: this is the check that our wrapper passes
    // `mapSuccess` through and returns what the library produced.
    val outcomes = countriesResponse(data = germanyData).outcomes()

    assertEquals(listOf(Outcome.Data(listOf(germanyModel), Origin.Network)), outcomes)
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
    // reporting — the library drops them before ever calling onException.
    countriesResponse(exception = CacheMissException(key = "Country:DE", fieldName = "name"))
      .outcomes()

    logWriter.assertCount(0)
  }

  @Test
  fun graphQlErrorsAreNotLogged() = runTest {
    // Only the exception branch reaches onException; API-level errors surface as DataError.Api.
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
