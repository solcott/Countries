package io.github.solcott.countries.shared.compose

import com.slack.circuit.foundation.Circuit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn

/**
 * The graph shared by every Compose consumer — the Android app today, and the Compose Multiplatform
 * iOS, desktop and web apps once `:presenter` and `:ui` are multiplatform. Each of those hosts a
 * platform entry point and calls `createGraph<ComposeGraph>()`; none of them declares a graph of
 * its own.
 *
 * It is declared here rather than in `:shared` because Metro aggregates `@ContributesTo`
 * contributions from the compile classpath of the module declaring `@DependencyGraph`. `:ui` has to
 * be visible *here* for its `CircuitProviders` and `Ui.Factory` multibindings to land in the graph
 * — an app module adding `:ui` downstream would be too late. `:shared`'s `CoreGraph` serves the
 * consumers that must not link Compose at all.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface ComposeGraph {
  val circuit: Circuit
}
