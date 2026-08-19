package io.github.solcott.countries.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A website.
 *
 * The two things that separate it from both other skins are structural rather than cosmetic, and
 * they are what [AppSkin.contentMaxWidth] and [AppSkin.contentPanel] exist for:
 * - **The content stops.** An app fills its window; a page has margins. 1120dp of content centred
 *   on a tinted background is most of the difference between the two, and no amount of restyling
 *   controls substitutes for it.
 * - **It sits on a panel.** A bordered, rounded surface floating above the page, rather than chrome
 *   welded to the window edges.
 *
 * On top of that: a page header with a wordmark instead of an app bar, generous type (16sp body on
 * a 24sp line, versus the desktop skin's 13 on 18), and rounded corners at web scale.
 *
 * [minInteractiveSize] is the one place this deliberately does **not** follow [DesktopSkin] down to
 * zero. The browser app is a phone app too — the same bundle, the same URL — and it has no way to
 * know which it is being used as, so it keeps a target a finger can hit.
 */
private val webShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
  )

/** Web type: bigger than desktop, and on longer lines. */
private val webTypography =
  Typography().run {
    copy(
      bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
      bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
      bodySmall = bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
      titleLarge =
        titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
      titleMedium =
        titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
      titleSmall =
        titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
      headlineMedium =
        headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
      labelLarge = labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
    )
  }

val WebSkin =
  AppSkin(
    name = "Web",
    lightColors = webLightScheme,
    darkColors = webDarkScheme,
    typography = webTypography,
    shapes = webShapes,
    chrome = Chrome.WebHeader,
    searchField = SearchFieldStyle.Outlined,
    detail = DetailLayout.Inspector,
    selection = SelectionStyle.InsetRounded,
    rowHorizontalPadding = 14.dp,
    rowVerticalPadding = 10.dp,
    rowMinHeight = 56.dp,
    rowSpacing = 14.dp,
    listDividers = false,
    sidebarTinted = true,
    listPaneWidth = 320.dp,
    selectionInset = 8.dp,
    contentPadding = 32.dp,
    contentMaxWidth = 1120.dp,
    contentPanel = true,
    detailFlagSize = 64.sp,
    // Wider than desktop's: the same words at 16sp instead of 13sp.
    inspectorLabelWidth = 124.dp,
    // Not 0.dp — see the note above. The browser app is also the phone app.
    minInteractiveSize = 40.dp,
    hoverAffordances = true,
    dimSupportingText = true,
  )
