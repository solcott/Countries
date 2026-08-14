import CountriesKit
import Foundation

/// A Kotlin presenter holder, as far as SwiftUI needs to know: a state flow and a way to stop it.
///
/// Both holders already satisfy this — `state` comes from their own declarations and `cancel()` is
/// inherited from the Kotlin `PresenterHolder` base — so the conformances below are empty and the
/// associated type is inferred.
protocol PresenterHolding: AnyObject {
  associatedtype UiState
  var state: SkieSwiftStateFlow<UiState> { get }
  func cancel()
}

extension CountryListPresenterHolder: PresenterHolding {}

extension CountryDetailPresenterHolder: PresenterHolding {}

/// Observes a Kotlin presenter and republishes its state for SwiftUI.
///
/// The work happens on the Kotlin side: Molecule recomposes the `@Composable` presenter and folds
/// each emission into a `StateFlow`, and SKIE exposes that as an `AsyncSequence` with a non-optional
/// `value`. All this adds is `@Observable` conformance and somewhere to hang the lifetime.
///
/// `final` with type aliases rather than a base class with two subclasses: `@Observable` belongs on
/// exactly one class in a hierarchy, and the two screens have nothing left to add beyond the holder
/// they are built from.
@Observable
@MainActor
final class PresenterModel<Holder: PresenterHolding> {

  private let holder: Holder

  private(set) var state: Holder.UiState

  init(_ holder: Holder) {
    self.holder = holder
    // Molecule's Immediate mode computes the first frame synchronously, so there is a real state
    // here before anything is awaited — no optional, and no empty first render.
    state = holder.state.value
  }

  /// Drives the presenter for as long as the view is on screen. Attach with `.task { }`.
  func observe() async {
    for await next in holder.state {
      state = next
    }
  }

  deinit {
    holder.cancel()
  }
}

typealias CountryListModel = PresenterModel<CountryListPresenterHolder>

typealias CountryDetailModel = PresenterModel<CountryDetailPresenterHolder>
