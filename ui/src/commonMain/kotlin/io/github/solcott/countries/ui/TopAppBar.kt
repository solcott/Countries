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
import io.github.solcott.countries.ui.resources.countries
import io.github.solcott.countries.ui.resources.home_24px
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CountriesTopAppBar(
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {
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
  },
) {
  TopAppBar(
    title = { Text(stringResource(Res.string.countries)) },
    colors =
      TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
      ),
    navigationIcon = navigationIcon,
    modifier = modifier,
  )
}

/** The default bar, as the list screen uses it: a disabled home icon, no back affordance. */
@ComponentWidthPreviews
@Composable
private fun CountriesTopAppBarPreview() {
  PreviewSurface { CountriesTopAppBar() }
}

/** The detail screen's variant, which swaps in a real back button. */
@PreviewLightDark
@Composable
private fun CountriesTopAppBarWithBackPreview() {
  PreviewSurface {
    CountriesTopAppBar(
      navigationIcon = {
        IconButton({}) {
          Icon(painterResource(Res.drawable.arrow_back_24px), contentDescription = "Back")
        }
      }
    )
  }
}
