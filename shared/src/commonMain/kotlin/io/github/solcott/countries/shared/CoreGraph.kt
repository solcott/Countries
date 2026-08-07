package io.github.solcott.countries.shared

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn
import io.github.solcott.countries.repository.ContinentRepository
import io.github.solcott.countries.repository.CountryRepository

/**
 * The graph for consumers that do **not** render Compose UI — principally a SwiftUI/UIKit iOS app,
 * which drives Circuit `Presenter`s directly and never needs a `Circuit` instance or any
 * `Ui.Factory`.
 *
 * Compose consumers use `ComposeGraph` in `:shared-compose` instead. There are two graphs rather
 * than one because Metro aggregates `@ContributesTo` contributions on the compile classpath of the
 * module declaring `@DependencyGraph`: a graph compiled without `:ui` cannot pick up its UI
 * factories later, and a SwiftUI app should not link Compose at all.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface CoreGraph {
  val countryRepository: CountryRepository

  val continentRepository: ContinentRepository
}
