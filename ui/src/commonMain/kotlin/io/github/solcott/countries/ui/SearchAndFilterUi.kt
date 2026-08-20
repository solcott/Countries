package io.github.solcott.countries.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import io.github.solcott.countries.ui.resources.close_24px
import io.github.solcott.countries.ui.resources.filter
import io.github.solcott.countries.ui.resources.filter_count
import io.github.solcott.countries.ui.resources.filter_list_24px
import io.github.solcott.countries.ui.resources.remove_filter
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
  Column(
    modifier
      .fillMaxWidth()
      .background(listPaneColor())
      .padding(vertical = 8.dp, horizontal = LocalAppSkin.current.rowHorizontalPadding)
  ) {
    Row(
      Modifier.fillMaxWidth(),
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
                SearchAndFilterScreen.Event.DropdownExpandedChanged(
                  !state.continentDropdownExpanded
                )
              )
            }
          ) {
            val selectedCount = state.selectedContinents.size
            // The badge is inside the button rather than around it: the button is the
            // ExposedDropdownMenuBox's anchor, and wrapping it would put a layout between the two.
            BadgedBox(badge = { if (selectedCount > 0) Badge { Text(selectedCount.toString()) } }) {
              Icon(
                painterResource(Res.drawable.filter_list_24px),
                contentDescription =
                  if (selectedCount == 0) stringResource(Res.string.filter)
                  else stringResource(Res.string.filter_count, selectedCount),
              )
            }
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

    val activeFilters = activeFiltersOf(state)
    if (activeFilters.isNotEmpty()) {
      ActiveFilterChips(activeFilters, Modifier.padding(top = 8.dp))
    }
  }
}

/**
 * One filter the user has switched on, as the chip row needs it.
 *
 * Deliberately not a `Continent`. Continents are the only filter today, but a language filter is
 * expected next, and the row below should not have to learn a second type to show it — it needs a
 * label and a way to clear one thing.
 */
@Immutable
private data class ActiveFilter(
  val label: String,
  val removeDescription: String,
  val onClear: () -> Unit,
)

/**
 * Everything currently filtering the list. Append the next filter's `map` here and the chip row and
 * the count both pick it up.
 */
@Composable
private fun activeFiltersOf(state: SearchAndFilterScreen.State): List<ActiveFilter> =
  state.selectedContinents.map { continent ->
    ActiveFilter(
      label = continent.name,
      removeDescription = stringResource(Res.string.remove_filter, continent.name),
      onClear = { state.eventSink(SearchAndFilterScreen.Event.ContinentToggled(continent)) },
    )
  }

/**
 * The active filters, each removable by tapping it.
 *
 * A `FlowRow` rather than a `Row`: the list pane is narrow on a phone and narrower still under
 * `WebSkin`'s content column, and chips that wrap read better than chips clipped off the edge.
 * Adding a second filter dimension makes wrapping the normal case rather than the exception.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFilterChips(filters: List<ActiveFilter>, modifier: Modifier = Modifier) {
  FlowRow(
    modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    filters.forEach { filter ->
      InputChip(
        selected = true,
        onClick = filter.onClear,
        label = { Text(filter.label) },
        trailingIcon = {
          Icon(
            painterResource(Res.drawable.close_24px),
            contentDescription = filter.removeDescription,
            modifier = Modifier.size(InputChipDefaults.IconSize),
          )
        },
      )
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

/**
 * The chip row on its own. Four filters is past what one line holds in the list pane, so this is
 * where the wrap is actually visible — inside [SearchAndFilterUi] the search field takes the width
 * first.
 */
@ComponentWidthPreviews
@Composable
private fun ActiveFilterChipsPreview() {
  PreviewSurface {
    ActiveFilterChips(
      previewContinents
        .filter { it.code in setOf("EU", "AS", "NA", "SA") }
        .map { continent ->
          ActiveFilter(
            label = continent.name,
            removeDescription = "Remove ${continent.name} filter",
            onClear = {},
          )
        }
    )
  }
}

/**
 * Several filters at once, which is the case the chip row wraps for. The two longest continent
 * names are picked deliberately — they are what overflows one line in the list pane's width.
 */
@ComponentWidthPreviews
@Composable
private fun SearchAndFilterUiManyFiltersPreview() {
  PreviewSurface {
    SearchAndFilterUi(
      searchAndFilterState(
        selectedContinents = previewContinents.filter { it.code in setOf("NA", "SA", "AN") }
      )
    )
  }
}

/** The chip row under the desktop skin, whose denser metrics it has to survive. */
@ComponentWidthPreviews
@Composable
private fun SearchAndFilterUiFiltersDesktopSkinPreview() {
  PreviewSurface(skin = DesktopSkin) {
    SearchAndFilterUi(
      searchAndFilterState(
        nameStartsWith = "Fra",
        selectedContinents = previewContinents.filter { it.code in setOf("EU", "AS") },
      )
    )
  }
}
