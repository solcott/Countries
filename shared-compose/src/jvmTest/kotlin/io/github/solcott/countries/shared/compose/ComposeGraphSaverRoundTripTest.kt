package io.github.solcott.countries.shared.compose

import com.slack.circuit.runtime.screen.restoreScreen
import dev.zacsweers.metro.createGraph
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The other half of [ComposeGraphSaverTest]: a saved screen comes back as itself, carrying its
 * arguments. That is what a back stack restore actually depends on, and it is what breaks if a
 * screen's class name — the polymorphic type discriminator `SerializableCircuitSaver` writes —
 * changes between app versions.
 *
 * **In `jvmTest` rather than `commonTest`, and it has to stay there.** `SavedState` is an
 * `android.os.Bundle` on Android, and the `androidHostTest` runner links the android.jar stubs,
 * where every `put`/`get` is a no-op. Saving there "succeeds" — it hands back a Bundle that
 * silently kept nothing — and restoring it yields null, so this test fails on that one runner for a
 * reason that has nothing to do with the code under test. Nothing warns; the assertion just reads
 * `expected:<CountryListScreen> but was:<null>`.
 *
 * The alternatives are worse than the coverage is worth. Robolectric would be a new dependency for
 * one assertion, and there is no intermediate test source set covering "every target but the
 * Android host" — the default hierarchy would want this file copied into `jvmTest`, `webTest` and
 * `appleTest`, or a hand-wired shared `srcDir`. `SavedState` on jvm is androidx's own
 * implementation, so one runner is enough to pin the discriminator.
 */
class ComposeGraphSaverRoundTripTest {

  private val circuitSaver = createGraph<ComposeGraph>().circuitSaver

  @Test
  fun countryListScreenRoundTrips() {
    val saved = assertNotNull(circuitSaver.save(CountryListScreen))
    assertEquals(CountryListScreen, circuitSaver.restoreScreen<CountryListScreen>(saved))
  }

  @Test
  fun countryDetailScreenRoundTripsWithItsCode() {
    val screen = CountryDetailScreen(code = "FR")
    val saved = assertNotNull(circuitSaver.save(screen))
    assertEquals(screen, circuitSaver.restoreScreen<CountryDetailScreen>(saved))
  }
}
