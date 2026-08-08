package io.github.solcott.countries.web

/**
 * What [BrowserHistory] should do to `window.history` for a given backstack change.
 *
 * Split out from the effect that executes it so every transition is testable without a DOM. It is a
 * six-way precedence decision and it has already produced two navigation bugs; the version that
 * lived inline could not be exercised at all.
 */
internal sealed interface HistoryAction {
  /** The URL already matches, or the screen has no URL of its own. */
  data object None : HistoryAction

  /** Walk the browser back, so an in-app pop leaves the forward button meaningful. */
  data object Back : HistoryAction

  /** Rewrite the current entry — a same-depth screen swap. */
  data class Replace(val route: String) : HistoryAction

  /** A new entry, so forward returns to the screen just left. */
  data class Push(val route: String) : HistoryAction

  /**
   * Make history match the backstack the app started with. [routes] is **root-first** and is
   * applied as `replaceState(routes.first())` followed by a `pushState` for each of the rest.
   */
  data class Seed(val routes: List<String>) : HistoryAction
}

/** [historyAction]'s `prevDepth` before the first reconciliation has happened. */
internal const val UNRECONCILED = -1

/**
 * Decides how `window.history` should move, given where the backstack was and where it now is.
 *
 * @param prevDepth backstack depth at the last reconciliation, or [UNRECONCILED] on the first run.
 * @param depth current backstack depth.
 * @param route URL of the top screen, or null if it has none.
 * @param currentHash `window.location.hash` as it stands right now.
 * @param fromPopState true when this change was caused by the browser rather than by the app.
 * @param stackRoutes every route in the backstack, **root-first**.
 */
internal fun historyAction(
  prevDepth: Int,
  depth: Int,
  route: String?,
  currentHash: String,
  fromPopState: Boolean,
  stackRoutes: List<String>,
): HistoryAction =
  when {
    route == null || stackRoutes.isEmpty() -> HistoryAction.None

    // The browser moved first and the URL is already what it should be. Touching history here
    // would fight the user's own back button.
    fromPopState -> HistoryAction.None

    // First run. The document has exactly one entry no matter how deep the backstack was seeded
    // from the URL, so a deep link needs the list synthesised underneath it — otherwise the
    // in-app back arrow walks out of the app. Seeding rather than pushing is also what keeps a
    // plain load of `/` from leaving a duplicate entry behind.
    prevDepth == UNRECONCILED -> HistoryAction.Seed(stackRoutes)

    depth > prevDepth -> HistoryAction.Push(route)

    depth < prevDepth -> HistoryAction.Back

    currentHash != route -> HistoryAction.Replace(route)

    else -> HistoryAction.None
  }
