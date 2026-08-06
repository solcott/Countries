package io.github.solcott.countries.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.api.Optional.Companion.absent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.countries.network.graphql.CountriesQuery
import io.github.solcott.countries.network.graphql.CountryDetailQuery
import io.github.solcott.countries.network.graphql.type.CountryFilterInput
import io.github.solcott.countries.network.graphql.type.StringQueryOperatorInput
import kotlinx.coroutines.flow.Flow

/**
 * Network boundary for the countries GraphQL API.
 *
 * Implementations return generated response types here, but Apollo generated *model* types never
 * cross past the `repository` mapping — see AGENTS.md.
 */
interface CountriesApi {
  /**
   * Streams the country list, optionally filtered.
   *
   * @param nameStartsWith when non-null/non-blank, keeps only countries whose name begins with
   *   these characters (case-insensitive). Null returns all names.
   * @param continents when not empty, keeps only countries in these continents. The API matches on
   *   the continent **code** (e.g. "EU", "AS"), not its display name. Empty list returns all
   *   continents.
   */
  fun countriesAsFlow(
    nameStartsWith: String? = null,
    continents: List<String> = emptyList(),
  ): Flow<ApolloResponse<CountriesQuery.Data>>

  fun countryAsFlow(code: String): Flow<ApolloResponse<CountryDetailQuery.Data>>
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class ApolloCountriesApi(private val apolloClient: ApolloClient) : CountriesApi {

  override fun countriesAsFlow(
    nameStartsWith: String?,
    continents: List<String>,
  ): Flow<ApolloResponse<CountriesQuery.Data>> {
    val filter =
      CountryFilterInput(
        name = nameStartsWith.asStartsWithOperator(),
        continent = continents.asInOperator(),
      )
    return apolloClient.query(CountriesQuery(Optional.present(filter))).toFlow()
  }

  override fun countryAsFlow(code: String): Flow<ApolloResponse<CountryDetailQuery.Data>> =
    apolloClient.query(CountryDetailQuery(code)).toFlow()
}

/**
 * Builds a case-insensitive "starts with" operator, or [Optional.absent] for a null/blank value so
 * the sub-field is omitted entirely (an omitted field matches all countries; an empty `{}` operator
 * would match none). Case-insensitivity is expressed with per-character classes because the server
 * compiles the pattern via `new RegExp(pattern)` with no flags — an `(?i)` prefix is unsupported.
 */
private fun String?.asStartsWithOperator(): Optional<StringQueryOperatorInput?> {
  val prefix = this?.trim().orEmpty()
  if (prefix.isEmpty()) return absent()
  val pattern = buildString {
    append('^')
    for (ch in prefix) {
      if (ch.isLetter() && ch.lowercaseChar() != ch.uppercaseChar()) {
        append('[').append(ch.lowercaseChar()).append(ch.uppercaseChar()).append(']')
      } else {
        append(ch.toString().escapeRegex())
      }
    }
  }
  return Optional.present(StringQueryOperatorInput(regex = Optional.present(pattern)))
}

/** Exact-match operator, or [Optional.absent] for a null/blank value. */
private fun String?.asEqualsOperator(): Optional<StringQueryOperatorInput?> {
  val value = this?.trim().orEmpty()
  return if (value.isEmpty()) absent()
  else Optional.present(StringQueryOperatorInput(eq = Optional.present(value)))
}

private fun List<String>.asInOperator(): Optional<StringQueryOperatorInput?> {
  return if (isEmpty()) absent()
  else Optional.present(StringQueryOperatorInput(`in` = Optional.present(this)))
}

private val REGEX_METACHARACTERS = Regex("""[.*+?^$(){}|\[\]\\]""")

/**
 * Escapes JS-regex metacharacters (Kotlin's Regex.escape emits `\Q…\E`, which the server rejects).
 */
private fun String.escapeRegex(): String = replace(REGEX_METACHARACTERS) { "\\${it.value}" }
