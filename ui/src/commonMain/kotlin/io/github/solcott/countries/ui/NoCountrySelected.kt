package io.github.solcott.countries.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.globe_24px
import io.github.solcott.countries.ui.resources.no_country_selected
import io.github.solcott.countries.ui.resources.no_country_selected_description
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The detail pane before anything is picked, on a window wide enough to show both panes.
 *
 * The stacked layout never shows this: there, opening a country is what puts the detail on screen
 * at all. It exists because two panes means one of them can be empty — the same reason the SwiftUI
 * app puts a `ContentUnavailableView` in the detail column, down to the globe and the wording.
 */
@Composable
fun NoCountrySelected(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
  ) {
    Icon(
      painterResource(Res.drawable.globe_24px),
      // Decorative: the title below says the same thing, and reading both is just noise.
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(56.dp),
    )
    Text(
      stringResource(Res.string.no_country_selected),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
    )
    Text(
      stringResource(Res.string.no_country_selected_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      // The pane can be most of a 1920dp window; a full-width line of body text is unreadable.
      modifier = Modifier.widthIn(max = 320.dp),
    )
  }
}

/** 360dp is the width at which the description wraps to three lines — the case worth looking at. */
@ComponentWidthPreviews
@Composable
private fun NoCountrySelectedPreview() {
  PreviewSurface { NoCountrySelected() }
}

@PreviewLightDark
@Composable
private fun NoCountrySelectedLightDarkPreview() {
  PreviewSurface { NoCountrySelected() }
}
