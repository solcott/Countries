package io.github.solcott.countries.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.arrow_back_24px
import io.github.solcott.countries.ui.resources.back
import io.github.solcott.countries.ui.resources.countries
import io.github.solcott.countries.ui.resources.home_24px
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The one app bar, for the whole app.
 *
 * `CountriesApp` owns the single [androidx.compose.material3.Scaffold] this sits in; neither screen
 * has one. That is what lets the two-pane layout put the list and the detail side by side under a
 * single bar rather than stacking two of them.
 *
 * [onBack] is null wherever there is nothing to go back to, which includes the whole two-pane
 * layout: with both panes live, closing the detail leaves the placeholder rather than a previous
 * screen. `NavigationSplitView` drops its back button on iPad for the same reason.
 */
@Composable
fun CountriesTopAppBar(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
  TopAppBar(
    title = { Text(stringResource(Res.string.countries)) },
    colors =
      TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
      ),
    navigationIcon = {
      if (onBack == null) {
        // Disabled rather than absent, so the title does not shift sideways when a back button
        // appears and disappears.
        IconButton(
          {},
          enabled = false,
          colors =
            IconButtonDefaults.iconButtonColors(
              disabledContentColor = MaterialTheme.colorScheme.onPrimary
            ),
        ) {
          Icon(painterResource(Res.drawable.home_24px), contentDescription = "Home")
        }
      } else {
        IconButton(onBack) {
          Icon(
            painterResource(Res.drawable.arrow_back_24px),
            contentDescription = stringResource(Res.string.back),
          )
        }
      }
    },
    modifier = modifier,
  )
}

/** No back affordance: the list on a narrow window, and both layouts on a wide one. */
@ComponentWidthPreviews
@Composable
private fun CountriesTopAppBarPreview() {
  PreviewSurface { CountriesTopAppBar() }
}

/** The stacked layout with a country open — the only case that gets a back button. */
@PreviewLightDark
@Composable
private fun CountriesTopAppBarWithBackPreview() {
  PreviewSurface { CountriesTopAppBar(onBack = {}) }
}
