plugins {
  id("library")
  id("org.jetbrains.kotlin.plugin.compose")
  alias(libs.plugins.metro)
}

android { buildFeatures { compose = true } }

dependencies {
  implementation(project(":model"))
  // Screens, state, and events live in :presenter. UI depends on presenter, never the reverse.
  implementation(project(":presenter"))
  // `api`: Circuit appears in CircuitProviders.provideCircuit's signature.
  api(libs.circuit.foundation)
  implementation(libs.circuit.runtime.ui)
  implementation(libs.circuit.codegen.annotations)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  // Not used from Kotlin, but the vector drawables tint with `?attr/colorControlNormal`, which is
  // an appcompat attribute. Resource linking fails without it. Both go when this module moves to
  // Compose Multiplatform resources.
  implementation(libs.androidx.appcompat)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
