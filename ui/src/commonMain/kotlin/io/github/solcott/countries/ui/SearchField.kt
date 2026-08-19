package io.github.solcott.countries.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.search_24px
import io.github.solcott.countries.ui.resources.search_by_name
import io.github.solcott.countries.ui.theme.DesktopSkin
import io.github.solcott.countries.ui.theme.LocalAppSkin
import io.github.solcott.countries.ui.theme.SearchFieldStyle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The search box at the top of the list pane, in whichever shape the skin asks for.
 *
 * Material's filled `TextField` is the single most Android-looking control the app draws, and it
 * cannot be made compact: its own minimum height is 56dp regardless of what
 * `LocalMinimumInteractiveComponentSize` says. So [SearchFieldStyle.Outlined] does not configure it
 * — it replaces it with a `BasicTextField` in a bordered row, which is the only way to get a field
 * that measures like a desktop control.
 */
@Composable
internal fun SearchField(state: TextFieldState, modifier: Modifier = Modifier) {
  when (LocalAppSkin.current.searchField) {
    SearchFieldStyle.Filled ->
      TextField(
        state,
        placeholder = { Text(stringResource(Res.string.search_by_name)) },
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
      )
    SearchFieldStyle.Outlined -> CompactSearchField(state, modifier)
  }
}

@Composable
private fun CompactSearchField(state: TextFieldState, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.small
  Row(
    modifier =
      modifier
        .heightIn(min = 28.dp)
        .background(colors.surface, shape)
        .border(1.dp, colors.outline, shape)
        .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      painterResource(Res.drawable.search_24px),
      // The placeholder beside it already says what the field is for.
      contentDescription = null,
      tint = colors.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
    )
    Box(Modifier.weight(1f)) {
      // BasicTextField has no placeholder slot, so it is drawn behind the field rather than by it.
      if (state.text.isEmpty()) {
        Text(
          stringResource(Res.string.search_by_name),
          style = MaterialTheme.typography.bodyLarge,
          color = colors.onSurfaceVariant,
        )
      }
      BasicTextField(
        state = state,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        lineLimits = TextFieldLineLimits.SingleLine,
      )
    }
  }
}

@ComponentWidthPreviews
@Composable
private fun SearchFieldPreview() {
  PreviewSurface { SearchField(TextFieldState("Fra")) }
}

/** Empty, which is the state the hand-drawn placeholder exists for. */
@PreviewLightDark
@Composable
private fun SearchFieldEmptyPreview() {
  PreviewSurface { SearchField(TextFieldState()) }
}

@ComponentWidthPreviews
@Composable
private fun CompactSearchFieldPreview() {
  PreviewSurface(skin = DesktopSkin) { SearchField(TextFieldState("Fra")) }
}

@PreviewLightDark
@Composable
private fun CompactSearchFieldEmptyPreview() {
  PreviewSurface(skin = DesktopSkin) { SearchField(TextFieldState()) }
}
