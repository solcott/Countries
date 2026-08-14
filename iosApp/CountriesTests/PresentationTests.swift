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
