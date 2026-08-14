package io.github.solcott.countries.apple

import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwiftNavigatorTest {

  @Test
  fun goToForwardsTheScreenAndReportsSuccess() {
    val seen = mutableListOf<Screen>()
    val navigator = SwiftNavigator(CountryListScreen, onGoTo = { seen += it }, onPop = {})

    val handled = navigator.goTo(CountryDetailScreen("CA"))

    assertTrue(handled)
    assertEquals(listOf<Screen>(CountryDetailScreen("CA")), seen.toList())
  }

  @Test
  fun goToDoesNotChangeTheMirrorUntilSwiftSyncsBack() {
    val navigator = SwiftNavigator(CountryListScreen, onGoTo = {}, onPop = {})

    navigator.goTo(CountryDetailScreen("CA"))

    // SwiftUI owns the stack: until it says otherwise, nothing has moved.
    assertEquals(listOf<Screen>(CountryListScreen), navigator.peekBackStack())

    navigator.syncFromSwift(listOf(CountryListScreen, CountryDetailScreen("CA")))

    assertEquals(CountryDetailScreen("CA"), navigator.peek())
  }

  @Test
  fun popForwardsAndReturnsTheTopScreenWhenNotAtRoot() {
    var pops = 0
    val navigator = SwiftNavigator(CountryListScreen, onGoTo = {}, onPop = { pops++ })
    navigator.syncFromSwift(listOf(CountryListScreen, CountryDetailScreen("CA")))

    val popped = navigator.pop()

    assertEquals(CountryDetailScreen("CA"), popped)
    assertEquals(1, pops)
  }

  @Test
  fun popAtRootIsIgnored() {
    var pops = 0
    val navigator = SwiftNavigator(CountryListScreen, onGoTo = {}, onPop = { pops++ })

    assertNull(navigator.pop())
    assertEquals(0, pops)
  }

  @Test
  fun syncFromSwiftFallsBackToTheRootRatherThanAnEmptyStack() {
    val navigator = SwiftNavigator(CountryListScreen, onGoTo = {}, onPop = {})
    navigator.syncFromSwift(listOf(CountryListScreen, CountryDetailScreen("CA")))

    navigator.syncFromSwift(emptyList())

    // peek() returning null would read as "no screens at all", which is never true of a running
    // app.
    assertEquals(CountryListScreen, navigator.peek())
  }
}
