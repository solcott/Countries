package io.github.solcott.countries.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

/**
 * Whether a key press should navigate back.
 *
 * Desktop has no system back gesture, and the back arrow in the top app bar is the only affordance
 * the shared UI offers. This adds the three bindings a desktop user reaches for: `Esc`, macOS's
 * `Cmd+[` and `Cmd+←`, and `Alt+←` everywhere else.
 *
 * The rule is a pure function for the same reason `historyAction()` is one in `:web` — a decision
 * welded to a `KeyEvent` cannot be tested without a window. The caller supplies [canPop], so the
 * guard against popping the root screen is part of the rule rather than something the effect
 * remembers to do.
 *
 * A bare `←` is deliberately not a shortcut: the list screen's filter field needs it.
 */
internal fun isBackShortcut(
  key: Key,
  type: KeyEventType,
  isMetaPressed: Boolean,
  isAltPressed: Boolean,
  canPop: Boolean,
): Boolean {
  if (!canPop || type != KeyEventType.KeyDown) return false
  return when (key) {
    // Unmodified, so it cannot collide with a system shortcut. On the list screen canPop is false,
    // which is also what keeps Esc away from the filter field — the only screen that has one.
    Key.Escape -> !isMetaPressed && !isAltPressed
    Key.DirectionLeft -> isMetaPressed || isAltPressed
    Key.LeftBracket -> isMetaPressed
    else -> false
  }
}
