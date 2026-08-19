package io.github.solcott.countries.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.arrow_back_24px
import io.github.solcott.countries.ui.resources.back
import io.github.solcott.countries.ui.resources.countries
import io.github.solcott.countries.ui.resources.hide_list
import io.github.solcott.countries.ui.resources.left_panel_close_24px
import io.github.solcott.countries.ui.resources.left_panel_open_24px
import io.github.solcott.countries.ui.resources.show_list
import io.github.solcott.countries.ui.theme.DesktopSkin
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Tall enough for a compact icon button and nothing more. Material's own bar is 64dp. */
private val ToolbarHeight = 40.dp

/**
 * The desktop skin's chrome, and [CountriesTopAppBar]'s counterpart — same four parameters, so
 * `CountriesAppScaffold` can pick between them without knowing which it got.
 *
 * The difference that matters is that this is drawn with a **line, not a colour**. Material fills
 * its bar with `primary`, which on a desktop window looks like a phone app wearing a title bar; a
 * desktop toolbar is the same surface as the content with a hairline separating the two. Everything
 * else follows from that: no `TopAppBarDefaults` colours, no elevation, and a title set in
 * `titleSmall` rather than `titleLarge`.
 *
 * There is no globe here. `CountriesTopAppBar` draws one where its navigation button would go so
 * the title does not slide sideways as the slot changes — but a decorative glyph in a toolbar's
 * control position reads as a broken button on desktop, so this reserves the space empty instead.
 */
@Composable
fun DesktopToolbar(
  modifier: Modifier = Modifier,
  title: String = stringResource(Res.string.countries),
  onBack: (() -> Unit)? = null,
  listCollapsed: Boolean = false,
  onToggleList: (() -> Unit)? = null,
) {
  Column(modifier) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .height(ToolbarHeight)
          .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ToolbarNavigation(onBack, listCollapsed, onToggleList)
      Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

/**
 * Back, the list toggle, or a hole the same size as either.
 *
 * The empty branch is not laziness: without it the title jumps left every time the window crosses
 * the two-pane breakpoint or a country is closed.
 */
@Composable
private fun ToolbarNavigation(
  onBack: (() -> Unit)?,
  listCollapsed: Boolean,
  onToggleList: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val slot = modifier.size(IconButtonDefaults.smallContainerSize())
  // Toolbar glyphs are secondary, and these two are dense enough that at `onSurface` they read as
  // solid blocks rather than icons — `left_panel_close` is mostly filled area by design.
  val colors =
    IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
  when {
    onBack != null ->
      IconButton(onBack, modifier = slot, colors = colors) {
        Icon(
          painterResource(Res.drawable.arrow_back_24px),
          contentDescription = stringResource(Res.string.back),
        )
      }
    onToggleList != null ->
      IconButton(onToggleList, modifier = slot, colors = colors) {
        Icon(
          painterResource(
            if (listCollapsed) Res.drawable.left_panel_open_24px
            else Res.drawable.left_panel_close_24px
          ),
          contentDescription =
            stringResource(if (listCollapsed) Res.string.show_list else Res.string.hide_list),
        )
      }
    else -> Box(slot)
  }
}

/** Two live panes with a country open: the toggle, offering to hide the list. */
@ComponentWidthPreviews
@Composable
private fun DesktopToolbarPreview() {
  PreviewSurface(skin = DesktopSkin) { DesktopToolbar(onToggleList = {}) }
}

/** Nothing to do: the empty slot holding the title in place. */
@PreviewLightDark
@Composable
private fun DesktopToolbarPlainPreview() {
  PreviewSurface(skin = DesktopSkin) { DesktopToolbar() }
}

/** The stacked layout with a country open — the one case that grows a back button. */
@PreviewLightDark
@Composable
private fun DesktopToolbarWithBackPreview() {
  PreviewSurface(skin = DesktopSkin) {
    DesktopToolbar(title = previewCountryDetail.name, onBack = {})
  }
}

/** The name that does not fit, which is what the ellipsis is for. */
@PreviewLightDark
@Composable
private fun DesktopToolbarLongTitlePreview() {
  PreviewSurface(skin = DesktopSkin) {
    DesktopToolbar(title = previewCountries.last().name, onBack = {})
  }
}

/** Collapsed, so the glyph points the other way. */
@PreviewLightDark
@Composable
private fun DesktopToolbarCollapsedPreview() {
  PreviewSurface(skin = DesktopSkin) { DesktopToolbar(listCollapsed = true, onToggleList = {}) }
}
