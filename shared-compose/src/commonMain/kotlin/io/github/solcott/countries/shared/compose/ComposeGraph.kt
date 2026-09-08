package io.github.solcott.countries.shared.compose

import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.screen.CircuitSaver
import com.slack.circuit.subcircuit.SubCircuit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn
import io.github.solcott.countries.network.NetworkProviders
import io.github.solcott.countries.shared.LoggingProviders
import io.github.solcott.countries.ui.CircuitProviders

/**
 * The graph shared by every Compose consumer — the Android app today, and the Compose Multiplatform
 * iOS, desktop and web apps alongside it. Each of those hosts a platform entry point and calls
 * `createGraph<ComposeGraph>()`; none of them declares a graph of its own.
 *
 * It is declared here rather than in `:shared` because Metro aggregates `@ContributesTo`
 * contributions from the compile classpath of the module declaring `@DependencyGraph`. `:ui` has to
 * be visible *here* for its `CircuitProviders` and `Ui.Factory` multibindings to land in the graph
 * — an app module adding `:ui` downstream would be too late. `:shared`'s `CoreGraph` serves the
 * consumers that must not link Compose at all.
 */
@SingleIn(AppScope::class)
@DependencyGraph(
  AppScope::class,
  bindingContainers = [CircuitProviders::class, LoggingProviders::class, NetworkProviders::class],
)
interface ComposeGraph {
  val circuit: Circuit

  /** Resolves the nested presenter/UI pairs that are not navigation destinations. */
  val subCircuit: SubCircuit

  /**
   * The same saver [circuit] carries, for the entry points that hoist their own back stack — `:web`
   * binds it to `window.history`, `:desktop` reaches it from `Window.onKeyEvent`. Both build the
   * stack before `CountriesApp` mounts `CircuitCompositionLocals`, so `LocalCircuitSaver` is not in
   * scope yet and `rememberSaveableBackStack` has to be told which saver to use.
   *
   * Every other consumer should let that local supply it rather than reaching in here.
   */
  val circuitSaver: CircuitSaver
}
