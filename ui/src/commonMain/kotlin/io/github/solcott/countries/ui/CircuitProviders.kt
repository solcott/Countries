package io.github.solcott.countries.ui

import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import com.slack.circuit.subcircuit.SubCircuit
import com.slack.circuit.subcircuit.SubPresenterFactory
import com.slack.circuit.subcircuit.SubUiFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface CircuitProviders {
  /**
   * Lives in `:ui` rather than in a graph module because a [Circuit] assembled with [Ui.Factory]
   * multibindings only means anything where Compose UI exists. A graph compiled without `:ui` on
   * its classpath — the SwiftUI iOS app — neither gets this nor needs it, since it drives
   * [Presenter]s directly.
   *
   * Both factory sets are contributed as multibindings by Metro's `@CircuitInject` codegen in
   * `:presenter` and `:ui`; this just assembles them.
   */
  @Provides
  fun provideCircuit(
    presenterFactories: Set<Presenter.Factory>,
    uiFactories: Set<Ui.Factory>,
  ): Circuit =
    Circuit.Builder().addPresenterFactories(presenterFactories).addUiFactories(uiFactories).build()

  /**
   * The [SubCircuit] every sub-screen is resolved through — `SearchAndFilterScreen` today.
   *
   * A separate registry from [Circuit] rather than part of it: a `SubPresenter` has no `Navigator`
   * and a `SubScreen` is never a navigation destination, so the two share no types beyond the
   * multibinding shape. Both factory sets come from Metro's `@SubCircuitInject` codegen, which
   * needs no KSP processor of its own.
   *
   * Whoever mounts the app has to put this in `LocalSubCircuit` — see `CountriesApp`. That local
   * defaults to null and `SubCircuitContent` requires it, so a missed provider is a runtime throw.
   */
  @Provides
  fun provideSubCircuit(
    presenterFactories: Set<SubPresenterFactory>,
    uiFactories: Set<SubUiFactory>,
  ): SubCircuit =
    SubCircuit.builder()
      .addPresenterFactories(presenterFactories)
      .addUiFactories(uiFactories)
      .build()
}
