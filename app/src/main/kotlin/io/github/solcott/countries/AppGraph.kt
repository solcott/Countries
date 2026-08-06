package io.github.solcott.countries

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
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
    presenterFactories: Set<Presenter.Factory>,
    uiFactories: Set<Ui.Factory>,
  ): Circuit =
    Circuit.Builder().addPresenterFactories(presenterFactories).addUiFactories(uiFactories).build()

  /**
   * The root logger. Modules inject this and re-tag it with [Logger.withTag] rather than reaching
   * for a global, so their logging is injectable and assertable in tests.
   *
   * This is the single place Kermit is configured for the app: additional writers — Crashlytics via
   * `kermit-crashlytics`, and so on — are added to [StaticConfig.logWriterList] here, and every
   * module picks them up automatically. [platformLogWriter] is Logcat on Android.
   */
  @Provides
  @SingleIn(AppScope::class)
  fun provideLogger(): Logger =
    Logger(config = StaticConfig(logWriterList = listOf(platformLogWriter())), tag = "Countries")
}
