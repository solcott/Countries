import CountriesKit
import Foundation

/// Owns navigation state, and adapts it to the Circuit `Navigator` the presenters expect.
///
/// SwiftUI is the single source of truth: ``selectedCode`` is what `NavigationSplitView` binds to,
/// and everything else follows from it. The Kotlin ``SwiftNavigator`` only forwards intent — when a
/// presenter navigates to a country the closure below sets ``selectedCode``, and the view updates
/// because of that, not because Kotlin pushed anything.
///
/// The Circuit `Screen`s that intent is really made of stay in Kotlin. This side deals in country
/// codes, which is all it ever needed.
///
/// Having exactly one writer is deliberate. `:web`'s `BrowserHistory` binds a backstack to
/// `window.history` in both directions and needs two guard flags to stop the binding feeding
/// itself; there is nothing to guard against here.
@Observable
@MainActor
final class NavigationModel {

  /// The country code being shown in the detail column, or `nil` for none.
  var selectedCode: String?

  @ObservationIgnored
  private var _navigator: SwiftNavigator!

  var navigator: SwiftNavigator { _navigator }

  init() {
    // Assigned after the stored properties above have their defaults, which is what lets the
    // closures capture `self`.
    _navigator = SwiftNavigator(
      onShowCountry: { [weak self] code in
        MainActor.assumeIsolated { self?.selectedCode = code }
      },
      onPop: {
        [weak self] in MainActor.assumeIsolated { self?.selectedCode = nil }
      }
    )
  }

  /// Tells Kotlin what is actually on screen.
  ///
  /// Call after every change to ``selectedCode``, including ones the navigator caused — it is
  /// idempotent, and that is cheaper than tracking which changes originated where.
  func syncToKotlin() {
    navigator.syncFromSwift(selectedCode: selectedCode)
  }
}
