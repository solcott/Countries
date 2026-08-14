package io.github.solcott.countries.apple

import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.navigation.NavStackList
import com.slack.circuit.runtime.screen.PopResult
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen

/**
 * The [Navigator] the presenters are given, forwarding navigation intent to SwiftUI.
 *
 * SwiftUI owns the navigation state — a `NavigationSplitView` selection — and this type is the
 * adapter, not the source of truth. [goTo] and [pop] invoke [onGoTo] and [onPop], the Swift side
 * updates its own state, and it calls [syncFromSwift] back so [screens] stays accurate for a
 * gesture the presenters never saw (a swipe back, or the Mac back button).
 *
 * That direction was chosen deliberately: `:web`'s `BrowserHistory` needed two guard flags to stop
 * a two-way binding feeding itself, and having exactly one writer avoids the whole class of bug.
 *
 * [screens] exists only to answer [peek] and [peekBackStack]. Neither `CountryListPresenter` nor
 * `CountryDetailPresenter` calls either, so it is close to vestigial — it is here because the
 * interface requires it, not because anything reads it.
 *
 * **Its Swift-facing API is country codes, not `Screen`s**, for the same reason the presenter state
 * is [CountryListUiState] rather than the Circuit state: keeping Circuit off the Swift surface is
 * what keeps the export small and stable. The `Screen`s are built here instead, which is also
 * simply where they belong — there are exactly two, and Swift has no use for either beyond a
 * country code.
 */
class SwiftNavigator(
  private val onShowCountry: (String) -> Unit,
  private val onPop: () -> Unit,
) : Navigator {

  private val root: Screen = CountryListScreen

  private var screens: List<Screen> = listOf(root)

  /**
   * Replaces the mirrored back stack with what SwiftUI is actually showing.
   *
   * Call this whenever the Swift-side navigation state changes, including changes this navigator
   * caused — it is idempotent, and cheaper than reasoning about which changes originated where.
   */
  fun syncFromSwift(selectedCode: String?) {
    screens =
      if (selectedCode == null) listOf(root) else listOf(root, CountryDetailScreen(selectedCode))
  }

  override fun goTo(screen: Screen): Boolean {
    // The list screen is the root and is never navigated *to*; the only forward move this app has
    // is opening a country.
    if (screen is CountryDetailScreen) onShowCountry(screen.code)
    return true
  }

  override fun pop(result: PopResult?): Screen? {
    // Popping the root is the host's business, not the navigator's: on iPhone there is nothing
    // behind the list, and on Mac the window close button is how you leave. `:desktop` leaves
    // onRootPop at its default no-op for the same reason.
    if (screens.size <= 1) return null
    val popped = screens.last()
    onPop()
    return popped
  }

  override fun peek(): Screen? = screens.lastOrNull()

  override fun peekBackStack(): List<Screen> = screens

  // Circuit's forward-history and multiple-back-stack features, neither of which this app uses.
  // Reported as unsupported rather than faked, which is what Navigator.NoOp does too.
  override fun peekNavStack(): NavStackList<Screen>? = null

  override fun forward(): Boolean = false

  override fun backward(): Boolean = false

  /**
   * Not reachable in this app — there is no flow to reset, and neither presenter calls it. If a
   * screen ever needs it, give this class a third closure rather than mutating [screens] here;
   * SwiftUI has to be the one to change what is on screen.
   */
  override fun resetRoot(newRoot: Screen, options: Navigator.StateOptions): List<Screen> = screens
}
