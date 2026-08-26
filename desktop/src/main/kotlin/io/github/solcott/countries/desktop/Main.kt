package io.github.solcott.countries.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.v2.Window
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowPositionProvider
import androidx.compose.ui.window.v2.WindowSizeProvider
import androidx.compose.ui.window.v2.rememberWindowState
import com.slack.circuit.backstack.rememberSaveableBackStack
import dev.zacsweers.metro.createGraph
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.shared.compose.ComposeGraph
import io.github.solcott.countries.ui.CountriesApp
import io.github.solcott.countries.ui.LocalFlagFontFamily
import io.github.solcott.countries.ui.theme.DesktopSkin

/**
 * `:ui` generates its `Res` class privately, so the window title cannot come from `strings.xml`.
 */
private const val WINDOW_TITLE = "Countries"

private val INITIAL_SIZE = DpSize(1100.dp, 800.dp)

/** Narrower or shorter than this and the list rows start truncating. */
private val MINIMUM_SIZE = DpSize(480.dp, 600.dp)

/**
 * The desktop entry point, and the jvm counterpart to `:app`'s `MainActivity` and `:web`'s
 * `main()`. All three do the same two things: read the registries out of [ComposeGraph], and hand
 * them to `CountriesApp`.
 *
 * The backstack is hoisted for the same reason `:web` hoists it — something outside `CountriesApp`
 * needs to drive it. There it is `window.history`; here it is the keyboard, which also drives the
 * list-pane collapse.
 */
fun main() {
  // Order matters, and nothing enforces it: `apple.awt.application.name` is read once when AWT
  // starts, and [applyTaskbarIcon] is the thing that starts it.
  applyApplicationName()
  applyTaskbarIcon()
  startApplication()
}

/**
 * Names the app for macOS.
 *
 * Without it the menu bar reads `MainKt`. A JVM launched outside an app bundle has no identity of
 * its own — LaunchServices registers this process as `net.java.openjdk.java` with the `java` binary
 * as its bundle path — so the main class name is the best macOS can do, and it is the same reason
 * the dock icon needed [applyTaskbarIcon]. Ignored off macOS.
 *
 * The packaged build does not rely on this: jpackage writes a real bundle whose `Info.plist` names
 * it.
 */
private fun applyApplicationName() {
  System.setProperty("apple.awt.application.name", WINDOW_TITLE)
}

// `androidx.compose.ui.window.v2`, new in Compose Multiplatform 1.12, and worth the opt-in for one
// reason: `minSize` is a parameter of [Window], so the window's floor is declared alongside its
// initial size instead of being reached through AWT in a LaunchedEffect after the fact. The v1
// WindowState could not express a minimum at all.
//
// The API's own KDoc warns it "may be moved to `androidx.compose.ui.window` before stabilization",
// so expect the imports above to churn once. Only this file is affected.
@OptIn(ExperimentalComposeUiApi::class)
private fun startApplication() = application {
  val graph = remember { createGraph<ComposeGraph>() }
  val backStack = rememberSaveableBackStack(root = CountryListScreen)
  val listCollapsed = rememberSaveable { mutableStateOf(false) }
  val windowState =
    rememberWindowState(
      initialBoundsProvider =
        WindowBoundsProvider(
          sizeProvider = WindowSizeProvider.Fixed(INITIAL_SIZE),
          positionProvider = WindowPositionProvider.CenteredOnScreen,
        )
    )

  Window(
    onCloseRequest = ::exitApplication,
    state = windowState,
    title = WINDOW_TITLE,
    icon = appIcon,
    minSize = MINIMUM_SIZE,
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
    // onRootPop is left at its default no-op: on desktop the window's close button is how you
    // leave, and popping past the list should not quit the app out from under the user.
    CompositionLocalProvider(LocalFlagFontFamily provides flagFontFamily) {
      // The one line that makes this a desktop app rather than an Android app in a window. Every
      // number behind it lives in `:ui`, so it is previewable there — see `AppSkin`.
      CountriesApp(
        circuit = graph.circuit,
        subCircuit = graph.subCircuit,
        skin = DesktopSkin,
        backStack = backStack,
        listCollapsed = listCollapsed,
      )
    }
  }
}
