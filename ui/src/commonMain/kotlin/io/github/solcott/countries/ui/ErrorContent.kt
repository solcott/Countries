package io.github.solcott.countries.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.solcott.countries.dataresult.DataError
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.retry
import io.github.solcott.countries.ui.theme.LocalAppSkin
import org.jetbrains.compose.resources.stringResource

@Composable
fun ErrorContent(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(LocalAppSkin.current.contentPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(text = message, textAlign = TextAlign.Center)
    Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
  }
}

@PreviewLightDark
@Composable
private fun ErrorContentPreview() {
  PreviewSurface { ErrorContent(message = DataError.Network.toUserMessage(), onRetry = {}) }
}

/** The longest message in the set — the one that decides whether the column wraps sensibly. */
@ComponentWidthPreviews
@Composable
private fun ErrorContentWidthsPreview() {
  PreviewSurface { ErrorContent(message = DataError.Serialization.toUserMessage(), onRetry = {}) }
}
