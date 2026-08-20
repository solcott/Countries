package io.github.solcott.countries.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.subcircuit.SubCircuitInject
import com.slack.circuit.subcircuit.SubUi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.solcott.countries.presenter.SearchAndFilterScreen
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.check_small_24px
import io.github.solcott.countries.ui.resources.filter
import io.github.solcott.countries.ui.resources.filter_list_24px
import io.github.solcott.countries.ui.theme.DesktopSkin
import io.github.solcott.countries.ui.theme.LocalAppSkin
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Registers [SearchAndFilterUi] as the sub-circuit's UI.
 *
 * Metro also accepts `@SubCircuitInject` straight on a top-level composable, which would make this
 * class unnecessary — but the `SubUiFactory` it generates for a *function* holds a reference to
 * that function, and lowering that reference crashes the Kotlin/JS and Kotlin/Wasm back-ends
 * (`UpgradeCallableReferences`, IndexOutOfBounds). Only the two web targets are affected; JVM,
 * Android and native compile it happily, so a build that skipped them would look fine.
 *
 * A class target is generated without the reference, and it costs nothing: [SearchAndFilterUi]
 * stays a plain composable with the default `modifier` the project's conventions ask for, and stays
 * previewable, which an override of `Content` would not be.
 */
@SubCircuitInject(SearchAndFilterScreen::class, AppScope::class)
@Inject
class SearchAndFilterSubUi : SubUi<SearchAndFilterScreen.State> {
  @Composable
  override fun Content(state: SearchAndFilterScreen.State, modifier: Modifier) {
    SearchAndFilterUi(state, modifier)
  }
}

/**
 * The search box and continent filter at the top of the list pane.
 *
 * A sub-circuit UI rather than a private composable in `CountryListUi`, so the filter it drives is
 * owned by a presenter of its own — see [SearchAndFilterScreen].
 *
 * It still binds [SearchAndFilterScreen.State.nameStartsWithText] straight into [SearchField]
 * rather than routing keystrokes through the event sink, which is the whole point of a
 * `TextFieldState`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndFilterUi(state: SearchAndFilterScreen.State, modifier: Modifier = Modifier) {
  Row(
    modifier
      .fillMaxWidth()
      .background(listPaneColor())
      .padding(vertical = 8.dp, horizontal = LocalAppSkin.current.rowHorizontalPadding),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SearchField(state.nameStartsWithText, modifier = Modifier.weight(1f))
    val continents = state.continentsState.data

    if (continents.isNotEmpty()) {

      ExposedDropdownMenuBox(
        state.continentDropdownExpanded,
        onExpandedChange = {
          state.eventSink(SearchAndFilterScreen.Event.DropdownExpandedChanged(it))
        },
      ) {
        IconButton(
          onClick = {
            state.eventSink(
              SearchAndFilterScreen.Event.DropdownExpandedChanged(!state.continentDropdownExpanded)
            )
          }
        ) {
          Icon(
            painterResource(Res.drawable.filter_list_24px),
            contentDescription = stringResource(Res.string.filter),
          )
        }
        ExposedDropdownMenu(
          expanded = state.continentDropdownExpanded,
          onDismissRequest = {
            state.eventSink(SearchAndFilterScreen.Event.DropdownExpandedChanged(false))
          },
          modifier = Modifier.width(200.dp),
        ) {
          continents.forEach { continent ->
            DropdownMenuItem(
              text = { Text(continent.name) },
              onClick = {
                state.eventSink(SearchAndFilterScreen.Event.DropdownExpandedChanged(false))
                state.eventSink(SearchAndFilterScreen.Event.ContinentToggled(continent))
              },
              trailingIcon = {
                if (state.selectedContinents.contains(continent)) {
                  Icon(painterResource(Res.drawable.check_small_24px), "Checked")
                }
              },
            )
          }
        }
      }
    }
  }
}

@ComponentWidthPreviews
@Composable
private fun SearchAndFilterUiPreview() {
  PreviewSurface {
    SearchAndFilterUi(
      searchAndFilterState(nameStartsWith = "Fra", selectedContinents = previewContinents.take(1))
    )
  }
}

/** Empty, which is what the list opens on. */
@ComponentWidthPreviews
@Composable
private fun SearchAndFilterUiEmptyPreview() {
  PreviewSurface { SearchAndFilterUi(searchAndFilterState()) }
}

/**
 * Continents still loading — the case that removes the filter control entirely, leaving the search
 * field the full width.
 */
@ComponentWidthPreviews
@Composable
private fun SearchAndFilterUiWithoutContinentsPreview() {
  PreviewSurface {
    SearchAndFilterUi(searchAndFilterState(continentsState = loadingState(emptyList())))
  }
}

/** The menu open with two continents ticked, which is the only way to see the check marks. */
@ComponentWidthPreviews
@Composable
private fun SearchAndFilterUiExpandedPreview() {
  PreviewSurface {
    SearchAndFilterUi(
      searchAndFilterState(
        selectedContinents = previewContinents.filter { it.code in setOf("EU", "AS") },
        continentDropdownExpanded = true,
      )
    )
  }
}

/** The desktop skin, where the search field is a compact bordered row rather than a filled one. */
@ComponentWidthPreviews
@Composable
private fun SearchAndFilterUiDesktopSkinPreview() {
  PreviewSurface(skin = DesktopSkin) {
    SearchAndFilterUi(searchAndFilterState(nameStartsWith = "Fra"))
  }
}
