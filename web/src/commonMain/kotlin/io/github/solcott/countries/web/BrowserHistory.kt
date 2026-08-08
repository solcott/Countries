// history.pushState/replaceState take the browser's opaque state object, which on wasmJs is a
// JsAny? and therefore behind the interop opt-in. We only ever pass null.
@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.countries.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.slack.circuit.backstack.SaveableBackStack
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * Keeps `window.history` and [backStack] in step, in both directions: in-app navigation writes the
 * URL, and the browser's back and forward buttons drive the backstack.
 *
 * Circuit has no web history integration to build on, and Compose Multiplatform's web `BackHandler`
 * is driven by a `NavigationEventDispatcher` that browser `popstate` does not feed — so this is the
 * seam, and it is hand-written.
 *
 * It mutates the backstack directly rather than going through the `Navigator`. For this app the two
 * are the same thing: `Navigator.goTo` is `backStack.push` and `Navigator.pop` is `backStack.pop`,
 * and the extra behaviour a `Navigator` adds (`resetRoot`, saved-state restore) belongs to
 * navigation patterns two screens do not have.
 *
 * Emits no UI, so per the project convention it takes no `modifier`.
 */
@Composable
internal fun BrowserHistory(backStack: SaveableBackStack) {
  val binding = remember { HistoryBinding() }

  // Reading these in composition is what makes this composable re-run on every navigation.
  val depth = backStack.size
  val route = backStack.topRecord?.screen?.toRoute()

  // Backstack -> URL.
  LaunchedEffect(route, depth) {
    if (route == null) return@LaunchedEffect
    when {
      // The change came from a popstate we are still processing; the URL is already correct.
      binding.fromPopState -> binding.fromPopState = false

      // Pushed a screen: a new history entry, so forward returns to it.
      depth > binding.depth -> window.history.pushState(null, "", route)

      // Popped from inside the app (the detail screen's back arrow). Walking the browser back
      // rather than rewriting the URL is what keeps the forward button meaningful. The popstate
      // this triggers is a no-op — the backstack has already moved.
      depth < binding.depth -> {
        binding.ignoreNextPopState = true
        window.history.back()
      }

      // First composition, or a same-depth screen swap.
      window.location.hash != route -> window.history.replaceState(null, "", route)
    }
    binding.depth = depth
  }

  // URL -> backstack.
  DisposableEffect(Unit) {
    val listener: (Event) -> Unit = {
      if (binding.ignoreNextPopState) {
        binding.ignoreNextPopState = false
      } else {
        val target = window.location.hash.toScreen()
        if (target != null && target != backStack.topRecord?.screen) {
          binding.fromPopState = true
          // Two screens deep at most, so "get to the target" is: unwind to the root, then push it
          // if it is not the root. Generalising this needs a real route-to-backstack diff.
          while (backStack.size > 1) backStack.pop()
          if (target != backStack.topRecord?.screen) backStack.push(target)
          binding.depth = backStack.size
        }
      }
    }
    window.addEventListener("popstate", listener)
    onDispose { window.removeEventListener("popstate", listener) }
  }
}

/**
 * The two "who moved first" flags and the last depth we reconciled at. Plain vars in a remembered
 * holder rather than `MutableState`: they are written from a DOM callback and read from an effect,
 * never from composition, so making them snapshot state would only invite spurious recompositions.
 */
private class HistoryBinding {
  var depth: Int = 0
  var fromPopState: Boolean = false
  var ignoreNextPopState: Boolean = false
}
