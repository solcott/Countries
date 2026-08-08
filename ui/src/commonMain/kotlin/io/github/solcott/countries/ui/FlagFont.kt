package io.github.solcott.countries.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/**
 * The font used for the flag emoji, or `null` to let the platform resolve them.
 *
 * `null` is right almost everywhere: Android, iOS and macOS all ship a font with the regional
 * indicator ligatures, and on web Compose Multiplatform downloads the Noto Color Emoji subset
 * itself (see the fonts note in AGENTS.md).
 *
 * Desktop is the exception, and only on two of its three platforms. Skia has no system font manager
 * of its own, so it renders whatever the OS hands it: Windows' Segoe UI Emoji has no flag glyphs at
 * all — by Microsoft's choice, not by omission — so every row comes out as a letter pair, and a
 * Linux install without Noto Color Emoji renders tofu. `:desktop` bundles a flag-only subset and
 * provides it here.
 *
 * This is the only platform seam in `:ui`. It is a font rather than a whole `Typography` because
 * the two composables that consume it render *nothing but* the flag, so the family needs no
 * fallback chain behind it.
 */
val LocalFlagFontFamily = staticCompositionLocalOf<FontFamily?> { null }
