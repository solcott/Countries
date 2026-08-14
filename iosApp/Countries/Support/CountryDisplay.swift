import CountriesModel
import Foundation

/// Placeholder for a field the API did not return. Matches `ABSENT` in `CountryDetailUi.kt`.
///
/// Computed rather than a `let`: a global `let` would resolve once at process start and hold the
/// first locale's value for the life of the app.
var absentValue: String {
  String(localized: .valueAbsent)
}

extension Country {

  /// The secondary line of a list row: capital and continent, skipping a missing capital rather
  /// than showing a gap.
  ///
  /// The join is a catalog format string rather than `joined(separator:)` so a translator can move
  /// the parts or change the separator. It is only used when both parts are present — with no
  /// capital there is nothing to separate, and running one value through a two-part format would
  /// strand the divider.
  var subtitle: String {
    guard let capital else { return continentName }
    return String(localized: .countryRowSubtitle(capital, continentName))
  }

  /// VoiceOver reads a flag emoji as its two regional-indicator letters, which is noise. Rows are
  /// combined into one element labelled with real words instead.
  var accessibilityDescription: String {
    guard !subtitle.isEmpty else { return name }
    return String(localized: .countryRowAccessibilityLabel(name, subtitle))
  }
}

extension CountryDetail {

  /// `.narrow` rather than the default `.standard`: standard renders "English and French", which is
  /// wrong for a form field listing values. Narrow keeps the comma form English already showed
  /// while still using whatever separator the locale calls for — Japanese, for one, does not use a
  /// comma here.
  var languageNames: String {
    languages.isEmpty
      ? absentValue
      : languages.map(\.name).formatted(.list(type: .and, width: .narrow))
  }
}

/// The row count, as a caption. macOS shows it as the window subtitle.
///
/// A free function next to the other display helpers rather than a computed property on the view,
/// because a `private var` on a `View` cannot be tested — and the plural is the one piece of this
/// worth testing.
///
/// The catalog entry carries the plural rules, and the generated symbol takes an `Int` because the
/// entry is written with `%lld`. English needs two forms and the old ternary happened to be right;
/// a language with a `few` or a `many` category would not have been served by it at all.
func countriesCaption(_ count: Int) -> String {
  String(localized: .countryListCountCaption(count))
}
