package io.github.solcott.countries.web

import kotlin.test.Test
import kotlin.test.assertEquals

private const val LIST = "#/"
private const val DETAIL = "#/country/AD"

/** Defaults describe a settled app sitting on the list, so each test states only what it varies. */
private fun action(
  prevDepth: Int = 1,
  depth: Int = 1,
  route: String? = LIST,
  currentHash: String = LIST,
  fromPopState: Boolean = false,
  stackRoutes: List<String> = listOf(LIST),
) = historyAction(prevDepth, depth, route, currentHash, fromPopState, stackRoutes)

class HistoryActionTest {

  /**
   * The regression. Loading `/` used to take the push branch, because the depth we compared against
   * started at zero rather than at "nothing reconciled yet". That left two history entries for one
   * screen, so backing out of the app took two presses instead of one.
   */
  @Test
  fun firstLoadAtTheRootSeedsOneEntryRatherThanPushing() {
    assertEquals(
      HistoryAction.Seed(listOf(LIST)),
      action(prevDepth = UNRECONCILED, depth = 1, currentHash = ""),
    )
  }

  /**
   * The other half of the same bug. A deep link opens two screens deep but the document has one
   * history entry, so the list has to be synthesised underneath — otherwise the in-app back arrow
   * calls `history.back()` and leaves the site.
   */
  @Test
  fun firstLoadAtADeepLinkSeedsTheListUnderneath() {
    assertEquals(
      HistoryAction.Seed(listOf(LIST, DETAIL)),
      action(
        prevDepth = UNRECONCILED,
        depth = 2,
        route = DETAIL,
        currentHash = DETAIL,
        stackRoutes = listOf(LIST, DETAIL),
      ),
    )
  }

  @Test
  fun growingTheStackPushesANewEntry() {
    assertEquals(
      HistoryAction.Push(DETAIL),
      action(prevDepth = 1, depth = 2, route = DETAIL, stackRoutes = listOf(LIST, DETAIL)),
    )
  }

  @Test
  fun shrinkingTheStackWalksTheBrowserBack() {
    assertEquals(HistoryAction.Back, action(prevDepth = 2, depth = 1, currentHash = DETAIL))
  }

  /** The browser already moved, so rewriting the URL here would fight the user's back button. */
  @Test
  fun aChangeCausedByPopStateLeavesHistoryAlone() {
    assertEquals(
      HistoryAction.None,
      action(prevDepth = 2, depth = 1, currentHash = LIST, fromPopState = true),
    )
  }

  /** popstate wins over every depth comparison, including a growing stack. */
  @Test
  fun popStateTakesPrecedenceOverAGrowingStack() {
    assertEquals(
      HistoryAction.None,
      action(
        prevDepth = 1,
        depth = 2,
        route = DETAIL,
        currentHash = DETAIL,
        fromPopState = true,
        stackRoutes = listOf(LIST, DETAIL),
      ),
    )
  }

  @Test
  fun sameDepthWithAStaleUrlRewritesTheCurrentEntry() {
    assertEquals(HistoryAction.Replace(LIST), action(currentHash = DETAIL))
  }

  @Test
  fun sameDepthWithAMatchingUrlDoesNothing() {
    assertEquals(HistoryAction.None, action())
  }

  @Test
  fun aScreenWithNoRouteDoesNothing() {
    assertEquals(HistoryAction.None, action(prevDepth = UNRECONCILED, route = null))
  }

  @Test
  fun anEmptyStackDoesNothing() {
    assertEquals(
      HistoryAction.None,
      action(prevDepth = UNRECONCILED, stackRoutes = emptyList()),
    )
  }

  /**
   * Push then in-app pop then push again, driving prevDepth the way the composable does. Pins that
   * the table composes, not just that each row works in isolation.
   */
  @Test
  fun aPushPopPushRoundTripProducesPushBackPush() {
    val stack = listOf(LIST, DETAIL)
    assertEquals(
      HistoryAction.Push(DETAIL),
      action(prevDepth = 1, depth = 2, route = DETAIL, stackRoutes = stack),
    )
    assertEquals(
      HistoryAction.Back,
      action(prevDepth = 2, depth = 1, currentHash = DETAIL),
    )
    assertEquals(
      HistoryAction.Push(DETAIL),
      action(prevDepth = 1, depth = 2, route = DETAIL, stackRoutes = stack),
    )
  }
}
