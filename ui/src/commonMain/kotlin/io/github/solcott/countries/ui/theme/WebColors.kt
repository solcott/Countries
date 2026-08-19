package io.github.solcott.countries.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A web palette: a tinted page, a panel floating on it, and one accent.
 *
 * The distinction [DesktopColors][desktopLightScheme] does not need is `background` versus
 * `surface`. On every other platform those are the same colour, because an app fills the window. A
 * web page does not: `background` is the page behind everything and `surface` is the card the
 * content actually sits on, and the gap between the two is most of what makes a layout read as a
 * page rather than as an app canvas.
 *
 * The accent is indigo rather than the desktop skin's blue, so that a screenshot of one is never
 * mistaken for the other.
 *
 * **These values are duplicated in `web/src/commonMain/resources/styles.css`**, which paints the
 * page before any Kotlin runs. They have to agree or the app flashes a different colour on load.
 */
private val accentLight = Color(0xFF4F46E5)
private val accentDark = Color(0xFF818CF8)

internal val webLightScheme =
  lightColorScheme(
    primary = accentLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E2FD),
    onPrimaryContainer = Color(0xFF241E76),
    secondary = Color(0xFF4B5563),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E2FD),
    onSecondaryContainer = Color(0xFF241E76),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF042F2A),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF601410),
    // The page behind the panel.
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF16181D),
    // The panel itself.
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFEDEFF2),
    onSurfaceVariant = Color(0xFF64707E),
    outline = Color(0xFFC5CBD3),
    // Every hairline in the web layout: the panel's border and the header's underline.
    outlineVariant = Color(0xFFE3E6EA),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2B2F36),
    inverseOnSurface = Color(0xFFF1F3F5),
    inversePrimary = accentDark,
    surfaceDim = Color(0xFFDDE1E6),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFCFD),
    // The list column inside the panel — a hair off white, just enough to separate it.
    surfaceContainer = Color(0xFFFAFBFC),
    surfaceContainerHigh = Color(0xFFF1F3F5),
    surfaceContainerHighest = Color(0xFFEAEDF0),
  )

internal val webDarkScheme =
  darkColorScheme(
    primary = accentDark,
    onPrimary = Color(0xFF191C4B),
    primaryContainer = Color(0xFF2E3170),
    onPrimaryContainer = Color(0xFFE4E2FD),
    secondary = Color(0xFF9CA3AF),
    onSecondary = Color(0xFF1E2127),
    secondaryContainer = Color(0xFF2E3170),
    onSecondaryContainer = Color(0xFFE4E2FD),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF042F2A),
    tertiaryContainer = Color(0xFF0F5850),
    onTertiaryContainer = Color(0xFFCCFBF1),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0D0F12),
    onBackground = Color(0xFFE7EAEE),
    surface = Color(0xFF15181C),
    onSurface = Color(0xFFE7EAEE),
    surfaceVariant = Color(0xFF232830),
    onSurfaceVariant = Color(0xFF98A2B0),
    outline = Color(0xFF454C57),
    outlineVariant = Color(0xFF262A30),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE7EAEE),
    inverseOnSurface = Color(0xFF2B2F36),
    inversePrimary = accentLight,
    surfaceDim = Color(0xFF0D0F12),
    surfaceBright = Color(0xFF31363E),
    surfaceContainerLowest = Color(0xFF090B0D),
    surfaceContainerLow = Color(0xFF15181C),
    surfaceContainer = Color(0xFF191C21),
    surfaceContainerHigh = Color(0xFF1F2329),
    surfaceContainerHighest = Color(0xFF272C33),
  )
