@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

plugins {
  id("kmp-library")
  // Required by the Compose Multiplatform plugin below, not by anything in this module — there is
  // no @Composable here. CMP fails configuration without it.
  id("org.jetbrains.kotlin.plugin.compose")
  // Not for the `compose.*` dependency accessors — this module declares no Compose dependency of
  // its own. It configures skiko's npm/webpack packaging, which arrives here through `:ui`, and
  // which the browser *test* bundle below cannot load without it. Same reason as in `:presenter`
  // and `:ui`.
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.metro)
}

kotlin {
  // See the same block in ui/build.gradle.kts. Compose Multiplatform 1.12 fails any Compose module
  // whose browser test bundle could not load skiko, and this module qualifies the moment it has a
  // test at all: ComposeGraphSaverTest builds a graph that reaches `:ui`. Without these two the js
  // run dies on `Cannot find module './skiko.mjs'` and the task then reports that it discovered no
  // tests, which points nowhere near the cause.
  js { binaries.executable() }

  wasmJs { binaries.executable() }

  // ComposeGraphSaverTest builds the real graph, which is the first thing in this module to reach
  // Apollo's SQLite normalized cache — and therefore SQLiter's _sqlite3_* symbols. The Apollo
  // Gradle plugin adds this flag to `:network`'s own native targets, and the Apple *app* gets it
  // from the Xcode target's OTHER_LDFLAGS (see `:apple`), but a downstream test executable
  // inherits neither: a Kotlin/Native klib records no linker options. Without it the tests compile
  // and then fail at `linkDebugTestIosSimulatorArm64` with a wall of undefined symbols.
  //
  // Test binaries only. Nothing else in this module links a native executable.
  targets.withType<KotlinNativeTarget>().configureEach {
    binaries.withType<TestExecutable>().configureEach { linkerOpts("-lsqlite3") }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":shared"))
      api(project(":presenter"))
      // On the compile classpath so Metro aggregates :ui's CircuitProviders and Ui.Factory
      // multibindings into ComposeGraph. See the comment on ComposeGraph.
      api(project(":ui"))
      api(libs.circuit.foundation)

      // Every module contributing to AppScope has to be on the compile classpath of the module
      // that declares the graph, because Metro resolves contributions there. It also has to be
      // `api` rather than `implementation`: contributed interfaces become *supertypes* of the
      // generated graph, so consumers of ComposeGraph need to see them too.
      api(project(":network"))
    }
  }
}
