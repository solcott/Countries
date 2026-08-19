package io.github.solcott.countries.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3, which is what Android should look like — so this is Android's skin, and the default
 * everywhere a skin is not passed.
 *
 * Every number here is what the app used before there were skins at all. Nothing in it is a design
 * decision made for this abstraction; it is the existing look, written down.
 */
val MaterialSkin =
  AppSkin(
    name = "Material",
    lightColors = lightScheme,
    darkColors = darkScheme,
    typography = AppTypography,
    shapes = Shapes(),
    chrome = Chrome.MaterialAppBar,
    searchField = SearchFieldStyle.Filled,
    detail = DetailLayout.Inline,
    selection = SelectionStyle.FullBleed,
    rowHorizontalPadding = 16.dp,
    rowVerticalPadding = 16.dp,
    // Material rows are sized by their padding and their content, not by a floor.
    rowMinHeight = Dp.Unspecified,
    rowSpacing = 16.dp,
    listDividers = true,
    sidebarTinted = false,
    // Mirrors `navigationSplitViewColumnWidth(ideal: 340)` in the SwiftUI app's `RootView`.
    listPaneWidth = 340.dp,
    selectionInset = 0.dp,
    contentPadding = 24.dp,
    contentMaxWidth = Dp.Unspecified,
    contentPanel = false,
    detailFlagSize = 56.sp,
    // Unused: Material renders the fields inline, not as an inspector.
    inspectorLabelWidth = 96.dp,
    // Material's own touch target, and what `LocalMinimumInteractiveComponentSize` already
    // defaults to — stated rather than inherited so the other skins have something to differ from.
    minInteractiveSize = 48.dp,
    hoverAffordances = false,
    dimSupportingText = false,
  )
