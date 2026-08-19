package io.github.solcott.countries.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.arrow_back_24px
import io.github.solcott.countries.ui.resources.back
import io.github.solcott.countries.ui.resources.countries
import io.github.solcott.countries.ui.resources.globe_24px
import io.github.solcott.countries.ui.resources.hide_list
import io.github.solcott.countries.ui.resources.left_panel_close_24px
import io.github.solcott.countries.ui.resources.left_panel_open_24px
import io.github.solcott.countries.ui.resources.show_list
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The one app bar, for the whole app.
 *
 * `CountriesApp` owns the single [androidx.compose.material3.Scaffold] this sits in; neither screen
 * has one. That is what lets the two-pane layout put the list and the detail side by side under a
 * single bar rather than stacking two of them.
 *
 * [title] is the selected country in the stacked layout, where the detail fills the window, and the
 * app's own name everywhere else — including beside two live panes, where the detail already
 * carries its own heading. See [LocalAppBarTitle] for how the name reaches the bar at all. It is
 * one line with an ellipsis because country names run long: "South Georgia and the South Sandwich
 * Islands" is in the list.
 *
 * The navigation slot holds whichever of three things applies, and never a decoration pretending to
 * be a button:
 * - [onBack], the stacked layout with a country open. Null elsewhere, including the whole two-pane
 *   layout: with both panes live, closing the detail leaves the placeholder rather than a previous
 *   screen, which is why `NavigationSplitView` drops its back button on iPad too.
 * - [onToggleList], two panes with a country open — the only case where hiding the list gains
 *   anything. [listCollapsed] picks the glyph and the label, both naming what the *tap will do*.
 * - otherwise a globe, which is decoration and is drawn as decoration: no click target and no
 *   content description, since the title beside it already reads "Countries". It is still sized
 *   like the buttons, so the title sits in the same place whichever branch is showing.
 */
@Composable
fun CountriesTopAppBar(
  modifier: Modifier = Modifier,
  title: String = stringResource(Res.string.countries),
  onBack: (() -> Unit)? = null,
  listCollapsed: Boolean = false,
  onToggleList: (() -> Unit)? = null,
) {
  TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    colors =
      TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
      ),
    navigationIcon = {
      when {
        onBack != null ->
          IconButton(onBack) {
            Icon(
              painterResource(Res.drawable.arrow_back_24px),
              contentDescription = stringResource(Res.string.back),
            )
          }
        onToggleList != null ->
          IconButton(onToggleList) {
            Icon(
              painterResource(
                if (listCollapsed) Res.drawable.left_panel_open_24px
                else Res.drawable.left_panel_close_24px
              ),
              contentDescription =
                stringResource(if (listCollapsed) Res.string.show_list else Res.string.hide_list),
            )
          }
        else ->
          // `IconButtonImpl`'s own sizing chain with the click left off, so a bare icon takes up
          // exactly what an IconButton does. TopAppBar reserves no slot for the navigation icon —
          // it measures whatever is there and starts the title at max(16.dp, thatWidth) — so any
          // branch here that measures differently slides the title sideways as the slot changes.
          //
          // `minimumInteractiveComponentSize` is load-bearing despite naming a touch target this
          // decoration does not have: it is what takes the box from the container's 40dp to 48dp,
          // and it reads LocalMinimumInteractiveComponentSize rather than hardcoding either number.
          // Drop it and the title jumps 8dp every time the globe swaps with a button.
          Box(
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .size(IconButtonDefaults.smallContainerSize()),
            contentAlignment = Alignment.Center,
          ) {
            Icon(painterResource(Res.drawable.globe_24px), contentDescription = null)
          }
      }
    },
    modifier = modifier,
  )
}

/** The list on a narrow window, and two panes with nothing picked: the globe, and nothing to do. */
@ComponentWidthPreviews
@Composable
private fun CountriesTopAppBarPreview() {
  PreviewSurface { CountriesTopAppBar() }
}

/** The stacked layout with a country open: the country's name, and a back button. */
@PreviewLightDark
@Composable
private fun CountriesTopAppBarWithBackPreview() {
  PreviewSurface { CountriesTopAppBar(title = previewCountryDetail.name, onBack = {}) }
}

/** Two panes with a country open — the list is showing, so the toggle offers to hide it. */
@PreviewLightDark
@Composable
private fun CountriesTopAppBarToggleExpandedPreview() {
  PreviewSurface { CountriesTopAppBar(listCollapsed = false, onToggleList = {}) }
}

/** The same, collapsed: the glyph flips to point the other way. */
@PreviewLightDark
@Composable
private fun CountriesTopAppBarToggleCollapsedPreview() {
  PreviewSurface { CountriesTopAppBar(listCollapsed = true, onToggleList = {}) }
}

/** The name that does not fit, which is what the ellipsis is for. */
@ComponentWidthPreviews
@Composable
private fun CountriesTopAppBarLongTitlePreview() {
  PreviewSurface { CountriesTopAppBar(title = previewCountries.last().name, onBack = {}) }
}
