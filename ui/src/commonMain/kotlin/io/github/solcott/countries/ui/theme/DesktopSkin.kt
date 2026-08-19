package io.github.solcott.countries.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A desktop application, on Windows, Linux and macOS.
 *
 * Three things separate it from [MaterialSkin], and they are the three that make a Compose app stop
 * reading as an Android app on a big screen:
 * - **Density.** 13sp body text and 6dp of vertical row padding instead of 16sp and 16dp, and
 *   `minInteractiveSize = 0.dp` so no Material control quietly grows itself to a 48dp touch target
 *   nobody is aiming a finger at.
 * - **Chrome drawn with lines, not colour.** A flat toolbar the same colour as the content with a
 *   hairline under it, instead of a bar filled with `primary`.
 * - **A sidebar rather than a list.** Tinted surface, no rules between rows, and an inset rounded
 *   selection — the shape every desktop shell uses to mark a persistent selection.
 *
 * Corner radii are small on purpose: 4dp reads as a desktop control, and Material's default `small`
 * (8dp) reads as a phone.
 */
private val desktopShapes =
  Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
  )

/**
 * Compact type.
 *
 * Only the styles this app actually renders are overridden; the rest keep Material's defaults,
 * which nothing reads. `bodyLarge` is the one that matters most — `MaterialTheme` hands it to every
 * bare `Text` through `ProvideTextStyle`, so it is the app's default size.
 */
private val desktopTypography =
  Typography().run {
    copy(
      bodyLarge = bodyLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
      bodyMedium = bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
      bodySmall = bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
      titleLarge =
        titleLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
      titleMedium =
        titleMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
      titleSmall =
        titleSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
      headlineMedium =
        headlineMedium.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
      labelLarge = labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp),
    )
  }

val DesktopSkin =
  AppSkin(
    name = "Desktop",
    lightColors = desktopLightScheme,
    darkColors = desktopDarkScheme,
    typography = desktopTypography,
    shapes = desktopShapes,
    chrome = Chrome.DesktopToolbar,
    searchField = SearchFieldStyle.Outlined,
    detail = DetailLayout.Inspector,
    selection = SelectionStyle.InsetRounded,
    rowHorizontalPadding = 10.dp,
    rowVerticalPadding = 6.dp,
    // A floor rather than a size: a row is as tall as its content needs, but never so short that
    // the list turns into a wall of text.
    rowMinHeight = 34.dp,
    rowSpacing = 10.dp,
    listDividers = false,
    sidebarTinted = true,
    listPaneWidth = 300.dp,
    selectionInset = 6.dp,
    contentPadding = 20.dp,
    contentMaxWidth = Dp.Unspecified,
    contentPanel = false,
    detailFlagSize = 48.sp,
    inspectorLabelWidth = 96.dp,
    // The single most load-bearing value here. Every Material control reads
    // `LocalMinimumInteractiveComponentSize` to decide its own floor, so zero is what stops the
    // whole control set measuring for a fingertip.
    minInteractiveSize = 0.dp,
    hoverAffordances = true,
    dimSupportingText = true,
  )
