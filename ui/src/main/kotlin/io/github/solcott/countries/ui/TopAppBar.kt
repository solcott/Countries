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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

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
        Icon(painterResource(R.drawable.home_24px), contentDescription = "Home")
      }
    },
) {
  TopAppBar(
      title = { Text(stringResource(R.string.countries)) },
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.primary,
              titleContentColor = MaterialTheme.colorScheme.onPrimary,
              navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
              actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
          ),
      navigationIcon = navigationIcon,
      modifier = modifier
  )
}
