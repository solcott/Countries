package io.github.solcott.countries.ui

import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
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
}
