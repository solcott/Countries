package io.github.solcott.countries.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * How the app looks on one platform.
 *
 * The Compose front ends share every composable in `:ui`; what differs between them is styling and
 * a small amount of structure, and this is where that difference lives. `MaterialSkin` is Android's
 * — Material 3 *is* the native look there — and `DesktopSkin` and `WebSkin` are the other two.
 *
 * **This is not a platform seam.** It is a parameter to `CountriesApp`, in the same way
 * `listCollapsed` and `onRootPop` are, and all of its values live in `:ui` next to the composables
 * that read them. Nothing here is `expect`/`actual`, nothing lives in a platform source set, and
 * every skin can be rendered from Android Studio — which is the whole reason to do it this way
 * rather than forking the UI per platform. `LocalFlagFontFamily` remains the module's one real
 * seam.
 *
 * Read it through [LocalAppSkin]. Composables take the tokens they need from there rather than as
 * parameters, because the alternative is threading a dozen values through every call site.
 */
@Immutable
data class AppSkin(
  /** Names the skin in previews. Not user-facing. */
  val name: String,
  val lightColors: ColorScheme,
  val darkColors: ColorScheme,
  val typography: Typography,
  val shapes: Shapes,

  // Structure: the handful of places where the skins render different composables rather than the
  // same one with different numbers.
  val chrome: Chrome,
  val searchField: SearchFieldStyle,
  val detail: DetailLayout,
  val selection: SelectionStyle,

  // The list pane.
  val rowHorizontalPadding: Dp,
  val rowVerticalPadding: Dp,
  /** [Dp.Unspecified] leaves the row's height to its padding, which is what Material does. */
  val rowMinHeight: Dp,
  /** Between the flag and the name. */
  val rowSpacing: Dp,
  val listDividers: Boolean,
  /** Whether the list pane sits on its own surface, the way a desktop sidebar does. */
  val sidebarTinted: Boolean,
  val listPaneWidth: Dp,
  /** How far a selected row is inset from the pane's edges. Zero for a full-bleed highlight. */
  val selectionInset: Dp,

  // Content.
  val contentPadding: Dp,
  /**
   * The widest the panes are allowed to get, with the rest of the window as margin.
   * [Dp.Unspecified] lets them fill it, which is what an app does and a web page does not.
   */
  val contentMaxWidth: Dp,
  /** Whether that content sits on a bordered panel above the window background. */
  val contentPanel: Boolean,
  /** The flag on the detail screen, which is set in points rather than through a text style. */
  val detailFlagSize: TextUnit,

  // Interaction.
  /**
   * Feeds `LocalMinimumInteractiveComponentSize`, which every Material control reads to decide its
   * own floor. 48dp is the touch target Android needs; a pointer platform wants none, and this one
   * value is what stops the whole control set measuring like a phone.
   */
  val minInteractiveSize: Dp,
  /** Hover highlights and a hand cursor: right with a pointer, meaningless with a finger. */
  val hoverAffordances: Boolean,
)

/** Which app-level chrome sits above the panes. */
enum class Chrome {
  /** Material's filled `TopAppBar`. */
  MaterialAppBar,
  /** A flat toolbar the same colour as the content, with a hairline under it. */
  DesktopToolbar,
  /** A page header with a wordmark, above a centred content column. */
  WebHeader,
}

enum class SearchFieldStyle {
  /** Material's filled `TextField`, floating label and all. */
  Filled,
  /** A compact bordered field with a leading search glyph. */
  Outlined,
}

enum class DetailLayout {
  /** `Capital: Bern`, one line per field. */
  Inline,
  /** A label column and a value column, the way a desktop inspector reads. */
  Inspector,
}

enum class SelectionStyle {
  /** The highlight spans the pane. */
  FullBleed,
  /** An inset rounded rectangle, the way a sidebar marks its selection. */
  InsetRounded,
}

/**
 * The skin in force.
 *
 * Defaults to [MaterialSkin] so a composable rendered outside [AppTheme] — a bare preview, a test —
 * still gets sensible values rather than throwing.
 */
val LocalAppSkin = staticCompositionLocalOf { MaterialSkin }
