package io.github.solcott.countries.ui

import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.CircuitSaver
import com.slack.circuit.runtime.ui.Ui
import com.slack.circuit.serialization.CircuitSerializable
import com.slack.circuit.serialization.CircuitSerializerRegistration
import com.slack.circuit.serialization.SerializableCircuitSaver
import com.slack.circuit.subcircuit.SubCircuit
import com.slack.circuit.subcircuit.SubPresenterFactory
import com.slack.circuit.subcircuit.SubUiFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object CircuitProviders {
  /**
   * How a [com.slack.circuit.runtime.screen.Screen] on a back stack survives being saved — process
   * death on Android, and `rememberSaveable` everywhere else.
   *
   * [registrations] is a multibinding like the two factory sets below, filled by the same Metro
   * Circuit codegen: one [CircuitSerializerRegistration] per [CircuitSerializable] screen in
   * `:presenter`. Nothing here is written by hand, and nothing has to be re-registered when a
   * screen is added — but a screen that *forgets* the annotation is not a build failure, it is a
   * throw the first time that screen is saved. See the `add-screen` skill.
   *
   * Scoped because [SerializableCircuitSaver] merges a `SerializersModule` when it is built, and
   * two things ask for one: [provideCircuit] below, and `ComposeGraph.circuitSaver` for the entry
   * points that hoist their own back stack.
   */
  @Provides
  @SingleIn(AppScope::class)
  fun provideCircuitSaver(registrations: Set<CircuitSerializerRegistration>): CircuitSaver =
    SerializableCircuitSaver(registrations)

  /**
   * Lives in `:ui` rather than in a graph module because a [Circuit] assembled with [Ui.Factory]
   * multibindings only means anything where Compose UI exists. A graph compiled without `:ui` on
   * its classpath — the SwiftUI iOS app — neither gets this nor needs it, since it drives
   * [Presenter]s directly.
   *
   * Both factory sets are contributed as multibindings by Metro's `@CircuitInject` codegen in
   * `:presenter` and `:ui`; this just assembles them.
   *
   * [setCircuitSaver][Circuit.Builder.setCircuitSaver] is not optional. Left off,
   * [CircuitCompositionLocals] silently falls back to `rememberDefaultCircuitSaver()`, which only
   * handles values the platform's own `SaveableStateRegistry` accepts — which our screens, being
   * serialized rather than `Parcelable`, are not.
   */
  @Provides
  @SingleIn(AppScope::class)
  fun provideCircuit(
    presenterFactories: Set<Presenter.Factory>,
    uiFactories: Set<Ui.Factory>,
    circuitSaver: CircuitSaver,
  ): Circuit =
    Circuit.Builder()
      .setCircuitSaver(circuitSaver)
      .addPresenterFactories(presenterFactories)
      .addUiFactories(uiFactories)
      .build()

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
  @SingleIn(AppScope::class)
  fun provideSubCircuit(
    presenterFactories: Set<SubPresenterFactory>,
    uiFactories: Set<SubUiFactory>,
  ): SubCircuit =
    SubCircuit.builder()
      .addPresenterFactories(presenterFactories)
      .addUiFactories(uiFactories)
      .build()
}
