package io.github.solcott.countries.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metro.createGraph
import io.github.solcott.countries.shared.compose.ComposeGraph
import io.github.solcott.countries.ui.CountriesApp
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
 */
fun main() = application {
  val circuit = remember { createGraph<ComposeGraph>().circuit }
  val windowState =
    rememberWindowState(size = INITIAL_SIZE, position = WindowPosition(Alignment.Center))

  Window(
    onCloseRequest = ::exitApplication,
    state = windowState,
    title = WINDOW_TITLE,
    icon = appIcon,
  ) {
    // AWT, and not expressible through WindowState. Without it the window can be dragged narrower
    // than the list rows tolerate.
    LaunchedEffect(Unit) { window.minimumSize = MINIMUM_SIZE }

    // onRootPop is left at its default no-op: on desktop the window's close button is how you
    // leave, and popping past the list should not quit the app out from under the user.
    CountriesApp(circuit = circuit)
  }
}
