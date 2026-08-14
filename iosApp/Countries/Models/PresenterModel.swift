import CountriesKit
import Foundation
import KotlinCoroutineSupport

/// A Kotlin presenter holder, as far as SwiftUI needs to know: a state flow and a way to stop it.
///
/// Both holders already satisfy this — `state` comes from their own declarations and `cancel()` is
/// inherited from the Kotlin `PresenterHolder` base — so the conformances below are empty and the
/// associated type is inferred.
///
/// This protocol is only possible because Swift export gives `StateFlow<T>` a *typed* Swift
/// counterpart, `KotlinTypedStateFlow<T>`, rather than erasing the element to its upper bound the
/// way it does for generic classes. Without a type to infer `UiState` from, there would be nothing
/// to write here.
protocol PresenterHolding: AnyObject {
  associatedtype UiState
  var state: any KotlinTypedStateFlow<UiState> { get }
  func cancel()
}

extension CountryListPresenterHolder: PresenterHolding {}

extension CountryDetailPresenterHolder: PresenterHolding {}

/// Observes a Kotlin presenter and republishes its state for SwiftUI.
///
/// The work happens on the Kotlin side: Molecule recomposes the `@Composable` presenter and folds
/// each emission into a `StateFlow`, which Swift export exposes with a non-optional typed `value`
/// and an `asAsyncSequence()`. All this adds is `@Observable` conformance and somewhere to hang the
/// lifetime.
///
/// `final` with type aliases rather than a base class with two subclasses: `@Observable` belongs on
/// exactly one class in a hierarchy, and the two screens have nothing left to add beyond the holder
/// they are built from.
@Observable
@MainActor
final class PresenterModel<Holder: PresenterHolding> {

  /// Exposed so views can send events: the Kotlin facade puts named methods on the holder rather
  /// than an `eventSink` closure on the state, so this is where they live.
  let holder: Holder

  private(set) var state: Holder.UiState

  init(_ holder: Holder) {
    self.holder = holder
    // Molecule's Immediate mode computes the first frame synchronously, so there is a real state
    // here before anything is awaited — no optional, and no empty first render.
    state = holder.state.value
  }

  /// Drives the presenter for as long as the view is on screen. Attach with `.task { }`.
  func observe() async {
    // `asAsyncSequence()` is throwing, so this takes a `try` and swallows the cancellation that
    // ends it. There is nothing to report: the only way out is the view going away, which is
    // exactly when `deinit` cancels the Kotlin scope.
    do {
      for try await next in holder.state.asAsyncSequence() {
        state = next
      }
    } catch {}
  }

  deinit {
    holder.cancel()
  }
}

typealias CountryListModel = PresenterModel<CountryListPresenterHolder>

typealias CountryDetailModel = PresenterModel<CountryDetailPresenterHolder>
