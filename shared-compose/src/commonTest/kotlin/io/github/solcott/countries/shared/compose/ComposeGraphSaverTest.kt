package io.github.solcott.countries.shared.compose

import dev.zacsweers.metro.createGraph
import io.github.solcott.countries.presenter.CountryDetailScreen
import io.github.solcott.countries.presenter.CountryListScreen
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Pins that every navigable `Screen` can actually be saved by the graph's `CircuitSaver`.
 *
 * This is the one thing about `@CircuitSerializable` that nothing else catches. A screen that
 * forgets the annotation — or grows a property with no serializer — compiles, links and navigates;
 * the `CircuitSerializerRegistration` multibinding just comes up one short, and the failure is an
 * `IllegalArgumentException` the first time that screen is *saved*: an Android rotation, process
 * death, or a desktop hot reload. `save` is where that throw lands, which is why asserting it
 * succeeds is the whole check. **Add every new screen here.**
 *
 * It goes through the real [ComposeGraph] rather than a hand-built `SerializableCircuitSaver` on
 * purpose. Registering the screens by hand would test kotlinx-serialization and prove nothing about
 * the codegen, which is the part that is easy to lose.
 *
 * Restoring is asserted separately, by `ComposeGraphSaverRoundTripTest` in `jvmTest` — that half
 * cannot run on all six targets, and the KDoc there says why.
 */
class ComposeGraphSaverTest {

  private val circuitSaver = createGraph<ComposeGraph>().circuitSaver

  @Test
  fun countryListScreenSaves() {
    assertNotNull(circuitSaver.save(CountryListScreen))
  }

  @Test
  fun countryDetailScreenSaves() {
    assertNotNull(circuitSaver.save(CountryDetailScreen(code = "FR")))
  }
}
