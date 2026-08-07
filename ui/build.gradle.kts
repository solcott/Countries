plugins {
  id("kmp-library")
  id("org.jetbrains.kotlin.plugin.compose")
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.metro)
}

compose.resources {
  publicResClass = false
  packageOfResClass = "io.github.solcott.countries.ui.resources"
}

kotlin {
  // The KMP Android plugin disables resource processing by default, which leaves
  // `variant.sources.assets` unavailable — and that is where Compose Multiplatform packages
  // composeResources on Android. Without this the app compiles and then throws
  // MissingResourceException at runtime.
  android { androidResources { enable = true } }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":model"))
      // Screens, state, and events live in :presenter. UI depends on presenter, never the reverse.
      implementation(project(":presenter"))
      // `api`: Circuit appears in CircuitProviders.provideCircuit's signature.
      api(libs.circuit.foundation)
      implementation(libs.circuit.runtime.ui)
      implementation(libs.circuit.codegen.annotations)

      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.compose.material3)
      // Generates the Res class from src/commonMain/composeResources — the multiplatform
      // replacement for the Android res/ directory this module used to have.
      implementation(libs.compose.components.resources)
    }
  }
}
