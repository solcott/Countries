package io.github.solcott.countries.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToggleListShortcutTest {

  private fun shortcut(
    key: Key,
    type: KeyEventType = KeyEventType.KeyDown,
    meta: Boolean = false,
    ctrl: Boolean = false,
  ) = isToggleListShortcut(key, type, meta, ctrl)

  @Test
  fun metaBackslashTogglesTheList() {
    assertTrue(shortcut(Key.Backslash, meta = true))
  }

  @Test
  fun ctrlBackslashTogglesTheList() {
    assertTrue(shortcut(Key.Backslash, ctrl = true))
  }

  /** The list screen's search field has to be able to type a backslash. */
  @Test
  fun bareBackslashDoesNotToggle() {
    assertFalse(shortcut(Key.Backslash))
  }

  /** One toggle per press: acting on the release as well would put the pane straight back. */
  @Test
  fun keyUpDoesNotToggle() {
    assertFalse(shortcut(Key.Backslash, type = KeyEventType.KeyUp, meta = true))
  }

  @Test
  fun anotherKeyWithTheSameModifierDoesNotToggle() {
    assertFalse(shortcut(Key.A, meta = true))
  }

  /** Back is the other shortcut in this window; the two must not both claim a key. */
  @Test
  fun theBackShortcutKeysDoNotToggle() {
    assertFalse(shortcut(Key.Escape))
    assertFalse(shortcut(Key.LeftBracket, meta = true))
    assertFalse(shortcut(Key.DirectionLeft, meta = true))
  }
}
