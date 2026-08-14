import CountriesDataResult
import CountriesModel
import CountriesUiState
import Testing

@testable import Countries

/// Covers the two pieces of presentation logic that live in Swift rather than in Kotlin.
///
/// Everything else — filtering, the search debounce, retry, cache-then-network — is tested in
/// `:presenter` and `:apple` and is not duplicated here. These two are Swift's own, because
/// `DataError.toUserMessage()` is `@Composable` and `Country.subtitle` has no Kotlin counterpart.
struct DataErrorMessageTests {

  @Test func networkErrorMentionsBeingOffline() {
    #expect(userMessage(for: DataError.Network.shared).contains("offline"))
  }

  @Test func httpErrorIncludesTheStatusCode() {
    #expect(userMessage(for: DataError.Http(code: 503)).contains("503"))
  }

  @Test func everyCaseProducesNonEmptyText() {
    let errors: [DataError] = [
      DataError.Network.shared,
      DataError.Http(code: 500),
      DataError.Api(messages: ["boom"], code: nil),
      DataError.Serialization.shared,
      DataError.Unknown(cause: nil, message: nil),
    ]
    for error in errors {
      #expect(!userMessage(for: error).isEmpty)
    }
  }
}

struct LoadPhaseTests {

  @Test func mapsEachStatusToItsPhase() {
    #expect(loadPhase(of: LoadStatus.Loading.shared).isLoading)
    #expect(loadPhase(of: LoadStatus.Idle.shared).isSettled)
    #expect(loadPhase(of: LoadStatus.Failed(error: DataError.Network.shared)).failure != nil)
  }

  @Test func loadingIsNotSettled() {
    // The distinction the detail screen leans on: "still loading" must not read as "not found".
    #expect(!loadPhase(of: LoadStatus.Loading.shared).isSettled)
  }
}

struct CountryDisplayTests {

  private func country(capital: String?) -> Country {
    Country(
      code: "CA",
      name: "Canada",
      emoji: "🇨🇦",
      capital: capital,
      continentName: "North America"
    )
  }

  @Test func subtitleJoinsCapitalAndContinent() {
    #expect(country(capital: "Ottawa").subtitle == "Ottawa · North America")
  }

  @Test func subtitleSkipsAMissingCapitalRatherThanLeavingAGap() {
    // 46 countries in the API have no capital, so this is the common case, not an edge case.
    #expect(country(capital: nil).subtitle == "North America")
  }

  @Test func accessibilityDescriptionUsesWordsNotTheFlag() {
    let described = country(capital: "Ottawa").accessibilityDescription
    #expect(described == "Canada, Ottawa · North America")
    #expect(!described.contains("🇨🇦"))
  }
}

/// The plural is the only piece of display logic here that a catalog can get wrong, and the only
/// one that was wrong before: the caption used to be a hand-written `count == 1` ternary, which
/// encodes English's two plural forms into the source.
///
/// These assert the *English* output, which is what the source language of the catalog resolves to
/// under test. They are a check on the `variations.plural` entry being present and correct, not on
/// any other language — a locale with a `few` category is the catalog's job, not Swift's.
struct CountriesCaptionTests {

  @Test func oneCountryUsesTheSingular() {
    #expect(countriesCaption(1) == "1 country")
  }

  @Test func severalCountriesUseThePlural() {
    #expect(countriesCaption(250) == "250 countries")
  }

  @Test func zeroUsesThePluralInEnglish() {
    // English puts zero in `other`, not in a category of its own. Worth pinning: it is the case a
    // hand-written `== 1` ternary gets right by accident and a bad catalog entry gets wrong.
    #expect(countriesCaption(0) == "0 countries")
  }
}
