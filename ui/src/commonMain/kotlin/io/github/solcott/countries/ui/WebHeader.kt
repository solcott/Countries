package io.github.solcott.countries.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.solcott.countries.ui.resources.globe_24px
import io.github.solcott.countries.ui.resources.hide_list
import io.github.solcott.countries.ui.resources.left_panel_close_24px
import io.github.solcott.countries.ui.resources.left_panel_open_24px
import io.github.solcott.countries.ui.resources.show_list
import io.github.solcott.countries.ui.theme.LocalAppSkin
import io.github.solcott.countries.ui.theme.WebSkin
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val HeaderHeight = 64.dp

/** The panel's inset from the content column, and the header's, so the two share a left edge. */
internal val WebPageGutter = 24.dp

/**
 * The web skin's chrome: a page header, not an app bar.
 *
 * Same four parameters as [CountriesTopAppBar] and [DesktopToolbar], so `CountriesAppScaffold`
 * picks between the three without knowing which it got. What differs is that this draws no
 * background of its own — it sits on the page tint, and the panel below supplies the structure a
 * filled bar would otherwise have to.
 *
 * The leading slot is a wordmark rather than a navigation control. That is the point of a site
 * header, and it is available here because the browser's own back button already exists: [onBack]
 * only ever arrives in the stacked layout, and it replaces the wordmark with the country's name for
 * exactly as long as the detail fills the window.
 *
 * [onToggleList] goes to the **right**. On desktop it belongs beside the pane it collapses; in a
 * page header, view controls sit opposite the wordmark.
 *
 * The row is capped at the skin's `contentMaxWidth` and centred, exactly as the panel below it is.
 * Without that the wordmark sits against the window edge while the content it names starts a couple
 * of hundred pixels in, which is the one thing that most gives a centred layout away.
 */
@Composable
fun WebHeader(
  modifier: Modifier = Modifier,
  title: String = stringResource(Res.string.countries),
  onBack: (() -> Unit)? = null,
  listCollapsed: Boolean = false,
  onToggleList: (() -> Unit)? = null,
) {
  Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    HeaderRow(title, onBack, listCollapsed, onToggleList)
  }
}

@Composable
private fun HeaderRow(
  title: String,
  onBack: (() -> Unit)?,
  listCollapsed: Boolean,
  onToggleList: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .widthIn(max = LocalAppSkin.current.contentMaxWidth)
        .fillMaxWidth()
        .height(HeaderHeight)
        .padding(horizontal = WebPageGutter),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (onBack != null) {
      IconButton(onBack) {
        Icon(
          painterResource(Res.drawable.arrow_back_24px),
          contentDescription = stringResource(Res.string.back),
        )
      }
    } else {
      Icon(
        painterResource(Res.drawable.globe_24px),
        // Decoration: the wordmark beside it already reads "Countries".
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(26.dp),
      )
    }
    Text(
      title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      // One weight in the row, not two. A `weight(1f, fill = false)` here plus a weighted spacer
      // splits the leftover space between them and strands the toggle mid-header.
      modifier = Modifier.weight(1f),
    )
    if (onToggleList != null) {
      IconButton(onToggleList) {
        Icon(
          painterResource(
            if (listCollapsed) Res.drawable.left_panel_open_24px
            else Res.drawable.left_panel_close_24px
          ),
          contentDescription =
            stringResource(if (listCollapsed) Res.string.show_list else Res.string.hide_list),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** The wordmark, which is what the header shows almost all of the time. */
@ComponentWidthPreviews
@Composable
private fun WebHeaderPreview() {
  PreviewSurface(skin = WebSkin) { WebHeader() }
}

@PreviewLightDark
@Composable
private fun WebHeaderLightDarkPreview() {
  PreviewSurface(skin = WebSkin) { WebHeader() }
}

/** Two live panes with a country open: the toggle, on the right where a view control belongs. */
@ComponentWidthPreviews
@Composable
private fun WebHeaderWithTogglePreview() {
  PreviewSurface(skin = WebSkin) { WebHeader(onToggleList = {}) }
}

/** The stacked layout: the country's name in place of the wordmark, and a back button. */
@PreviewLightDark
@Composable
private fun WebHeaderWithBackPreview() {
  PreviewSurface(skin = WebSkin) { WebHeader(title = previewCountryDetail.name, onBack = {}) }
}

/** The name that does not fit, which is what the ellipsis is for. */
@ComponentWidthPreviews
@Composable
private fun WebHeaderLongTitlePreview() {
  PreviewSurface(skin = WebSkin) { WebHeader(title = previewCountries.last().name, onBack = {}) }
}
