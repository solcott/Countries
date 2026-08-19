package io.github.solcott.countries.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A neutral desktop palette: greys for everything structural, one blue for anything that means
 * "selected" or "actionable".
 *
 * Deliberately not any one vendor's colours. `:desktop` ships Windows, Linux and macOS, and macOS
 * already has the hand-written SwiftUI app in `iosApp/` — so the two platforms this palette really
 * serves are Windows and Linux, and chasing either one's design language would look foreign on the
 * other. Neutral greys plus a system-ish accent read as native on all three and wrong on none.
 *
 * The three surface levels do the work Material's tonal elevation would otherwise do: `surface` is
 * the content pane, `surfaceContainer` is the sidebar behind it, and `outlineVariant` is the
 * hairline that separates them. Desktop chrome is drawn with lines, not shadows.
 */
private val accentLight = Color(0xFF2563EB)
private val accentDark = Color(0xFF5C93F5)

internal val desktopLightScheme =
  lightColorScheme(
    primary = accentLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7FD),
    onPrimaryContainer = Color(0xFF102A63),
    secondary = Color(0xFF4B5563),
    onSecondary = Color(0xFFFFFFFF),
    // The selected sidebar row. Tinted rather than saturated, because on desktop the selection is
    // a persistent state rather than a momentary highlight and a full accent fill is loud at rest.
    secondaryContainer = Color(0xFFDCE7FD),
    onSecondaryContainer = Color(0xFF102A63),
    tertiary = Color(0xFF3F6212),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3F0C8),
    onTertiaryContainer = Color(0xFF1F3306),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF601410),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1D1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1D1F),
    surfaceVariant = Color(0xFFEDEEF1),
    onSurfaceVariant = Color(0xFF6B6E73),
    outline = Color(0xFFC7CACE),
    outlineVariant = Color(0xFFE1E3E6),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2E3033),
    inverseOnSurface = Color(0xFFF1F2F4),
    inversePrimary = accentDark,
    surfaceDim = Color(0xFFDFE1E4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FA),
    // The sidebar.
    surfaceContainer = Color(0xFFF2F3F5),
    surfaceContainerHigh = Color(0xFFEBECEF),
    surfaceContainerHighest = Color(0xFFE5E7EA),
  )

internal val desktopDarkScheme =
  darkColorScheme(
    primary = accentDark,
    onPrimary = Color(0xFF0B1B36),
    primaryContainer = Color(0xFF26375A),
    onPrimaryContainer = Color(0xFFD6E2FB),
    secondary = Color(0xFF9CA3AF),
    onSecondary = Color(0xFF20242B),
    secondaryContainer = Color(0xFF26375A),
    onSecondaryContainer = Color(0xFFD6E2FB),
    tertiary = Color(0xFFA3C97A),
    onTertiary = Color(0xFF1F3306),
    tertiaryContainer = Color(0xFF2F4A12),
    onTertiaryContainer = Color(0xFFE3F0C8),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF1B1D20),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF1B1D20),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF2A2C30),
    onSurfaceVariant = Color(0xFF9A9EA6),
    outline = Color(0xFF4A4E54),
    outlineVariant = Color(0xFF2F3237),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6E8EB),
    inverseOnSurface = Color(0xFF2E3033),
    inversePrimary = accentLight,
    surfaceDim = Color(0xFF141619),
    surfaceBright = Color(0xFF35383D),
    surfaceContainerLowest = Color(0xFF101215),
    surfaceContainerLow = Color(0xFF1B1D20),
    // The sidebar, darker than the content it sits beside — the inverse of the light scheme, and
    // what every dark desktop shell does.
    surfaceContainer = Color(0xFF17181B),
    surfaceContainerHigh = Color(0xFF212328),
    surfaceContainerHighest = Color(0xFF2A2C31),
  )
