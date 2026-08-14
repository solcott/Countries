import CountriesKit
import Foundation

/// `ContentState.status`, as something Swift can switch over.
///
/// This deliberately does *not* live in an extension on `ContentState`. `ContentState<T>` is a
/// Kotlin generic class, which reaches Swift as an Objective-C lightweight generic, and Swift
/// forbids an extension of one from touching its type parameters at runtime — the compiler rejects
/// the whole extension, not just the members that use `T`. Reading `status` off the concrete
/// property and mapping it here sidesteps that entirely.
///
/// It also replaces `isLoading` and `errorOrNull` from `ContentState.kt`, which are Kotlin
/// extension properties and so are absent from the framework header: extensions are not members,
/// and the Obj-C header only carries members.
enum LoadPhase {
  case loading
  case idle
  case failed(any DataError)
}

/// SKIE turns the sealed `LoadStatus` into an exhaustive Swift enum, so this cannot silently miss a
/// case the way an `if let` chain over `as?` casts would.
func loadPhase(of status: any LoadStatus) -> LoadPhase {
  switch onEnum(of: status) {
  case .loading:
    return .loading
  case .idle:
    return .idle
  case .failed(let failed):
    return .failed(failed.error)
  }
}

extension LoadPhase {

  var isLoading: Bool {
    if case .loading = self { return true }
    return false
  }

  var failure: (any DataError)? {
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
func userMessage(for error: any DataError) -> String {
  switch onEnum(of: error) {
  case .network:
    return String(
      localized: "You appear to be offline. Check your connection and try again.",
      comment: "Shown when a request failed with no usable response")
  case .http(let http):
    return String(
      localized: "The server returned an error (\(http.code)).",
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
