import CountriesKit
import Foundation

/// Placeholder for a field the API did not return. Matches `ABSENT` in `CountryDetailUi.kt`.
let absentValue = "—"

extension Country {

  /// The secondary line of a list row: capital and continent, skipping a missing capital rather
  /// than showing a gap.
  var subtitle: String {
    [capital, continentName].compactMap { $0 }.joined(separator: " · ")
  }

  /// VoiceOver reads a flag emoji as its two regional-indicator letters, which is noise. Rows are
  /// combined into one element labelled with real words instead.
  var accessibilityDescription: String {
    subtitle.isEmpty ? name : "\(name), \(subtitle)"
  }
}

extension CountryDetail {

  var languageNames: String {
    languages.isEmpty ? absentValue : languages.map(\.name).joined(separator: ", ")
  }
}
