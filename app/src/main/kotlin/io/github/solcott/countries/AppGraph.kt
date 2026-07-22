package io.github.solcott.countries

import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface AppGraph {

    val circuit: Circuit

    /**
     * Presenter and UI factories are contributed as multibindings by Metro's @CircuitInject codegen
     * in :presenter and :ui — this just assembles them.
     */
    @Provides
      fun provideCircuit(
        presenterFactories: Set<Presenter.Factory>, uiFactories: Set<Ui.Factory>,
    ): Circuit =
        Circuit.Builder().addPresenterFactories(presenterFactories)
            .addUiFactories(uiFactories)
            .build()
}
