package io.github.solcott.countries.apple

import dev.zacsweers.metro.createGraph
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.shared.CoreGraph

/**
 * The one Kotlin object the Swift app constructs. Everything else it touches comes from here.
 *
 * This is the Apple counterpart to `:shared-compose`: it owns [CoreGraph] and hands back presenter
 * holders that are already wired. `createGraph` is a reified Metro function and so is invisible to
 * Swift, which is the immediate reason this type exists — but the better reason is that dependency
 * wiring should not live in an Xcode project any more than it lives in `MainActivity`.
 *
 * [CoreGraph] rather than `ComposeGraph` because there is no Compose UI here: no `Circuit`
 * instance, no `Ui.Factory`. Hold one instance for the lifetime of the app — the graph is
 * `@SingleIn(AppScope::class)`, and a second one would mean a second Apollo client and cache.
 *
 * Named `CountriesCore` rather than `CountriesKit`: a Kotlin class whose name matches the exported
 * module's name is silently renamed to `CountriesKit_` in the generated Swift.
 */
class CountriesCore {

  private val graph: CoreGraph = createGraph<CoreGraph>()

  fun countryListPresenter(navigator: SwiftNavigator): CountryListPresenterHolder =
    CountryListPresenterHolder(
      navigator = navigator,
      countryRepository = graph.countryRepository,
      continentRepository = graph.continentRepository,
    )

  fun countryDetailPresenter(
    code: String,
    navigator: SwiftNavigator,
  ): CountryDetailPresenterHolder =
    CountryDetailPresenterHolder(
      screen = CountryDetailScreen(code),
      navigator = navigator,
      countryRepository = graph.countryRepository,
    )
}
