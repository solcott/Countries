package io.github.solcott.countries.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackShortcutTest {

  private fun shortcut(
    key: Key,
    type: KeyEventType = KeyEventType.KeyDown,
    meta: Boolean = false,
    alt: Boolean = false,
    canPop: Boolean = true,
  ) = isBackShortcut(key, type, meta, alt, canPop)

  @Test
  fun escapeGoesBack() {
    assertTrue(shortcut(Key.Escape))
  }

  @Test
  fun metaLeftBracketGoesBack() {
    assertTrue(shortcut(Key.LeftBracket, meta = true))
  }

  @Test
  fun metaArrowGoesBack() {
    assertTrue(shortcut(Key.DirectionLeft, meta = true))
  }

  @Test
  fun altArrowGoesBack() {
    assertTrue(shortcut(Key.DirectionLeft, alt = true))
  }

  /** The list screen's filter field needs a bare arrow key for cursor movement. */
  @Test
  fun bareArrowDoesNotGoBack() {
    assertFalse(shortcut(Key.DirectionLeft))
  }

  /** Likewise a bare bracket, which is an ordinary character. */
  @Test
  fun bareLeftBracketDoesNotGoBack() {
    assertFalse(shortcut(Key.LeftBracket))
  }

  /** Firing on both down and up would pop twice per press. */
  @Test
  fun keyUpDoesNotGoBack() {
    assertFalse(shortcut(Key.Escape, type = KeyEventType.KeyUp))
  }

  /**
   * On the root screen there is nothing to pop, which is what keeps Esc out of the filter field.
   */
  @Test
  fun nothingGoesBackWhenTheStackIsAtItsRoot() {
    assertFalse(shortcut(Key.Escape, canPop = false))
    assertFalse(shortcut(Key.DirectionLeft, meta = true, canPop = false))
  }

  /** Cmd+Esc is macOS's Force Quit chord; it must not be swallowed as a back gesture. */
  @Test
  fun modifiedEscapeDoesNotGoBack() {
    assertFalse(shortcut(Key.Escape, meta = true))
    assertFalse(shortcut(Key.Escape, alt = true))
  }

  @Test
  fun unrelatedKeysDoNotGoBack() {
    assertFalse(shortcut(Key.A))
    assertFalse(shortcut(Key.DirectionRight, meta = true))
  }
}
