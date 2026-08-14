import CountriesDataResult
import CountriesUiState
import Foundation

/// `LoadStatus`, as something Swift can switch over.
///
/// Kept as a Swift enum of its own even though `sealedType()` now produces one, because the three
/// helpers below read far better at the call sites than repeated `switch`es, and because this is
/// what `isLoading`/`errorOrNull`/`isNotFound` — Kotlin extension properties, which do not cross to
/// Swift as members — used to provide.
enum LoadPhase {
  case loading
  case idle
  case failed(DataError)
}

/// Swift export maps the sealed `LoadStatus` to a Swift enum through a generated `sealedType()`, so
/// this cannot silently miss a case the way an `if let` chain over `as?` casts would. That
/// exhaustiveness is what the project is on Kotlin 2.4.20 for — the sealed-type support landed
/// there, and on 2.4.10 a `default:` would be mandatory here.
func loadPhase(of status: LoadStatus) -> LoadPhase {
  switch status.sealedType() {
  case .loading:
    return .loading
  case .idle:
    return .idle
  case .failed(let failed):
    return .failed(failed.value.error)
  }
}

extension LoadPhase {

  var isLoading: Bool {
    if case .loading = self { return true }
    return false
  }

  var failure: (DataError)? {
    if case .failed(let error) = self { return error }
    return nil
  }

  /// A request that finished having produced nothing — `isNotFound` in `CountryDetailScreen.kt`.
  /// Distinguishes "no such country" from "still loading", which look identical from `data` alone.
  var isSettled: Bool {
    if case .idle = self { return true }
    return false
  }
}

/// The five `DataError` cases, as user-facing text.
///
/// `:ui` has this already in `DataErrorMessage.kt`, but that version is `@Composable` and reads
/// from compose-resources, so it cannot be linked here. The wording matches deliberately — the apps
/// should say the same thing.
func userMessage(for error: DataError) -> String {
  switch error.sealedType() {
  case .network:
    return String(
      localized: "You appear to be offline. Check your connection and try again.",
      comment: "Shown when a request failed with no usable response")
  case .http(let http):
    return String(
      localized: "The server returned an error (\(http.value.code)).",
      comment: "Shown when a request returned a non-success HTTP status")
  case .api:
    return String(
      localized: "The server was unable to complete your request.",
      comment: "Shown when the GraphQL response carried errors")
  case .serialization:
    return String(
      localized: "Something went wrong reading the response.",
      comment: "Shown when a response could not be decoded")
  case .unknown:
    return String(localized: "An unknown error occurred.", comment: "Fallback error message")
  }
}
