package io.github.solcott.countries.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.foundation.NavDecoration
import com.slack.circuit.foundation.ProvideRecordLifecycle
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.navigation.NavArgument
import com.slack.circuit.runtime.navigation.NavStackList
import io.github.solcott.countries.presenter.CountryDetailScreen

/**
 * [calculated] with the two Material defaults this app does not want.
 *
 * Both have to be set through the constructor rather than [PaneScaffoldDirective.copy], which
 * exposes neither — and which quietly resets `shouldAutoFocusCurrentDestination` to true on the way
 * through, so a `copy()` anywhere after this would undo it.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun countriesPaneDirective(calculated: PaneScaffoldDirective): PaneScaffoldDirective =
  PaneScaffoldDirective(
    maxHorizontalPartitions = calculated.maxHorizontalPartitions,
    horizontalPartitionSpacerSize = calculated.horizontalPartitionSpacerSize,
    // Vertical reflow on a tall compact window is not a layout this app wants; the directive
    // otherwise raises this to 2 and splits the panes top and bottom.
    maxVerticalPartitions = 1,
    verticalPartitionSpacerSize = calculated.verticalPartitionSpacerSize,
    defaultPanePreferredWidth = calculated.defaultPanePreferredWidth,
    defaultPanePreferredHeight = calculated.defaultPanePreferredHeight,
    excludedBounds = calculated.excludedBounds,
    // Off, because "move focus to the pane that just became current" means something unwanted for
    // this app's list pane: its first focusable child is the search field, so closing a country put
    // a blinking cursor in the search box and sometimes raised the soft keyboard. Nothing here
    // navigates by keyboard between panes, so there is nothing to trade away.
    shouldAutoFocusCurrentDestination = false,
  )

/** Mirrors `navigationSplitViewColumnWidth(ideal: 340)` in the SwiftUI app's `RootView`. */
private val ListPaneWidth = 340.dp

/**
 * Lays the back stack out as a list pane and a detail pane, side by side when [directive] allows
 * two horizontal partitions and one at a time when it does not.
 *
 * **The back stack stays the only selection state.** `[list]` renders the list beside
 * [NoCountrySelected]; `[list, detail]` renders both for real. Nothing here holds a selected
 * country — which is what lets `:web` keep binding the stack to `window.history`, `:desktop` keep
 * popping it with the back shortcut, and Android keep using system back, all unchanged. Material's
 * own `rememberListDetailPaneScaffoldNavigator` was rejected for exactly that reason: it would be a
 * second navigation state competing with the first.
 *
 * A [NavDecoration] rather than a layout wrapped around `NavigableCircuitContent`, because
 * `content` is a per-record `movableContentOf` that Circuit has already wrapped in that record's
 * saveable- and retained-state providers. Calling it once per pane keeps per-record state scoping
 * for free; composing `CircuitContent(CountryListScreen)` next to the navigable content instead
 * would silently lose it.
 *
 * One `ListDetailPaneScaffold` serves both layouts on purpose. Crossing the breakpoint then changes
 * only the [ThreePaneScaffoldValue], so a window resized past it does not tear the list's
 * composition down and refetch; an `if (wide) Row else Box` would.
 *
 * [listCollapsed] gives the detail the whole window. It applies only where it buys something — two
 * live panes with a country open — so it is inert in the stacked layout, and it undoes itself when
 * the country is closed rather than leaving [NoCountrySelected] telling you to pick from a list
 * that is not on screen.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Stable
internal class ListDetailNavDecoration(
  private val directive: PaneScaffoldDirective,
  private val listCollapsed: Boolean,
) : NavDecoration {

  @Composable
  override fun <T : NavArgument> DecoratedContent(
    args: NavStackList<T>,
    navigator: Navigator,
    modifier: Modifier,
    content: @Composable (T) -> Unit,
  ) {
    // CountryListScreen is the root and is never navigated *to*, so the stack is [list] or
    // [list, detail] and these two reads describe it completely.
    val listArg = args.root
    val detailArg = args.top.takeIf { it.screen is CountryDetailScreen }

    val hideList = listCollapsed && directive.maxHorizontalPartitions > 1 && detailArg != null

    ListDetailPaneScaffold(
      directive = directive,
      value =
        if (hideList) {
          // Built by hand because no AdaptStrategy can say "hide a pane there is room for", so
          // calculateThreePaneScaffoldValue has no way to express this. AnimatedPane is an
          // AnimatedVisibility over `value[role] != Hidden`, so the scaffold animates the swap
          // itself: the list slides out left while the detail grows into the space.
          ThreePaneScaffoldValue(
            primary = PaneAdaptedValue.Expanded, // ListDetailPaneScaffoldRole.Detail
            secondary = PaneAdaptedValue.Hidden, // ListDetailPaneScaffoldRole.List
            tertiary = PaneAdaptedValue.Hidden,
          )
        } else {
          calculateThreePaneScaffoldValue(
            maxHorizontalPartitions = directive.maxHorizontalPartitions,
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
            currentDestination =
              ThreePaneScaffoldDestinationItem(
                pane =
                  if (detailArg == null) ListDetailPaneScaffoldRole.List
                  else ListDetailPaneScaffoldRole.Detail,
                contentKey = detailArg?.key,
              ),
          )
        },
      modifier = modifier,
      listPane = {
        AnimatedPane(Modifier.preferredWidth(ListPaneWidth)) { OnScreen { content(listArg) } }
      },
      detailPane = {
        AnimatedPane {
          if (detailArg == null) NoCountrySelected(Modifier.fillMaxSize())
          else OnScreen { content(detailArg) }
        }
      },
    )
  }
}

/**
 * Marks a record active for as long as its pane is composed.
 *
 * Circuit pauses the presenter of any record that is not the stack's current one — `Circuit`'s
 * `presentWithLifecycle` defaults to true — and `NavigableCircuitContent` derives that from the
 * stack alone. In the two-pane layout the detail is current, so the list beside it would be paused:
 * `pausableState` drops the producer from composition and replays its last value, which stops
 * `produceRetainedState` collecting. Typing in the search field would update the text and never
 * re-query, with nothing to see in a log.
 *
 * A hidden `AnimatedPane` does not compose its content at all, so anything reaching here is on
 * screen and should be running.
 */
@Composable
private fun OnScreen(content: @Composable () -> Unit) {
  ProvideRecordLifecycle(isActive = true, content = content)
}
