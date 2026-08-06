package io.github.solcott.countries.shared

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
interface LoggingProviders {
  /**
   * The root logger. Modules inject this and re-tag it with [Logger.withTag] rather than reaching
   * for a global, so their logging is injectable and assertable in tests.
   *
   * This is the single place Kermit is configured, for every platform: additional writers —
   * Crashlytics via `kermit-crashlytics`, and so on — are added to [StaticConfig]'s writer list
   * here, and every module that injects a [Logger] picks them up. [platformLogWriter] resolves to
   * Logcat on Android, NSLog on Apple, and the console on js/wasm.
   */
  @Provides
  @SingleIn(AppScope::class)
  fun provideLogger(): Logger =
    Logger(config = StaticConfig(logWriterList = listOf(platformLogWriter())), tag = "Countries")
}
