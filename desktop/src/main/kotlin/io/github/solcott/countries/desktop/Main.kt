package io.github.solcott.countries.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.slack.circuit.backstack.rememberSaveableBackStack
import dev.zacsweers.metro.createGraph
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.shared.compose.ComposeGraph
import io.github.solcott.countries.ui.CountriesApp
import io.github.solcott.countries.ui.LocalFlagFontFamily
import io.github.solcott.countries.ui.theme.DesktopSkin
import java.awt.Dimension

/**
 * `:ui` generates its `Res` class privately, so the window title cannot come from `strings.xml`.
 */
private const val WINDOW_TITLE = "Countries"

private val INITIAL_SIZE = DpSize(1100.dp, 800.dp)
private val MINIMUM_SIZE = Dimension(480, 600)

/**
 * The desktop entry point, and the jvm counterpart to `:app`'s `MainActivity` and `:web`'s
 * `main()`. All three do the same two things: read the `Circuit` out of [ComposeGraph], and hand it
 * to `CountriesApp`.
 *
 * The backstack is hoisted for the same reason `:web` hoists it — something outside `CountriesApp`
 * needs to drive it. There it is `window.history`; here it is the keyboard, which also drives the
 * list-pane collapse.
 */
fun main() = application {
  val circuit = remember { createGraph<ComposeGraph>().circuit }
  val backStack = rememberSaveableBackStack(root = CountryListScreen)
  val listCollapsed = rememberSaveable { mutableStateOf(false) }
  val windowState =
    rememberWindowState(size = INITIAL_SIZE, position = WindowPosition(Alignment.Center))

  Window(
    onCloseRequest = ::exitApplication,
    state = windowState,
    title = WINDOW_TITLE,
    icon = appIcon,
    // Returning true consumes the event; false lets it reach the focused composable — which is why
    // each branch has to be tried in turn rather than combined.
    onKeyEvent = { event ->
      when {
        isBackShortcut(
          key = event.key,
          type = event.type,
          isMetaPressed = event.isMetaPressed,
          isAltPressed = event.isAltPressed,
          canPop = backStack.size > 1,
        ) -> {
          backStack.pop()
          true
        }
        isToggleListShortcut(
          key = event.key,
          type = event.type,
          isMetaPressed = event.isMetaPressed,
          isCtrlPressed = event.isCtrlPressed,
        ) -> {
          listCollapsed.value = !listCollapsed.value
          true
        }
        else -> false
      }
    },
  ) {
    // AWT, and not expressible through WindowState. Without it the window can be dragged narrower
    // than the list rows tolerate.
    LaunchedEffect(Unit) { window.minimumSize = MINIMUM_SIZE }

    // onRootPop is left at its default no-op: on desktop the window's close button is how you
    // leave, and popping past the list should not quit the app out from under the user.
    CompositionLocalProvider(LocalFlagFontFamily provides flagFontFamily) {
      // The one line that makes this a desktop app rather than an Android app in a window. Every
      // number behind it lives in `:ui`, so it is previewable there — see `AppSkin`.
      CountriesApp(
        circuit = circuit,
        skin = DesktopSkin,
        backStack = backStack,
        listCollapsed = listCollapsed,
      )
    }
  }
}
