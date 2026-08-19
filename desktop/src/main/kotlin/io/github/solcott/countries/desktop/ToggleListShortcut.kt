package io.github.solcott.countries.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

/**
 * Whether a key press should collapse or restore the list pane.
 *
 * `Cmd+\` on macOS, `Ctrl+\` elsewhere — the binding editors and browsers use for their own
 * sidebars, and the same shape as [isBackShortcut]: a pure function, because a rule welded to a
 * `KeyEvent` cannot be tested without a window.
 *
 * Modified rather than bare, because the list screen owns a text field and an unmodified `\` has to
 * reach it. Unlike [isBackShortcut] there is no state guard: the shared UI ignores the flag in the
 * stacked layout, so toggling in a narrow window simply decides what the layout will look like once
 * the window is widened.
 */
internal fun isToggleListShortcut(
  key: Key,
  type: KeyEventType,
  isMetaPressed: Boolean,
  isCtrlPressed: Boolean,
): Boolean =
  type == KeyEventType.KeyDown && key == Key.Backslash && (isMetaPressed || isCtrlPressed)
