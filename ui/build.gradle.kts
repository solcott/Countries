plugins {
  id("library")
  id("org.jetbrains.kotlin.plugin.compose")
  alias(libs.plugins.metro)
}

android {
  // The detail screen is intentionally an XML layout hosted in AndroidView. See AGENTS.md.
  buildFeatures {
    compose = true
    viewBinding = true
  }
}

dependencies {
  implementation(project(":model"))
  // Screens, state, and events live in :presenter. UI depends on presenter, never the reverse.
  implementation(project(":presenter"))
  implementation(libs.circuit.runtime.ui)
  implementation(libs.circuit.codegen.annotations)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
