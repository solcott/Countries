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

  // See the same block in presenter/build.gradle.kts: CMP 1.12's
  // checkComposeUiTestConfigurationFor{Js,WasmJs} fails any Compose module whose browser test
  // bundle could not load skiko. It fires off the target's test task existing, not off there
  // being test sources, so this module needs it despite having none yet.
  js { binaries.executable() }

  wasmJs { binaries.executable() }

  sourceSets {
    // The AndroidX Compose BOM, scoped to Android because that is the only place it applies.
    // Compose Multiplatform's `foundation` redirects to androidx `foundation-android` here, and
    // without the BOM that lands on whatever CMP requested rather than the version the rest of the
    // Android build uses. Non-Android targets are unaffected and stay on CMP 1.12.0-rc01.
    // `project.dependencies.platform(...)`, not a bare `platform(...)`: a KMP source-set
    // dependency handler is not Gradle's DependencyHandler and has no platform() of its own.
    androidMain.dependencies {
      implementation(project.dependencies.platform(libs.androidx.compose.bom))
    }

    commonMain.dependencies {
      implementation(project(":dataresult"))
      implementation(project(":model"))
      implementation(project(":uistate"))
      // Screens, state, and events live in :presenter. UI depends on presenter, never the reverse.
      implementation(project(":presenter"))
      // `api`: Circuit appears in CircuitProviders.provideCircuit's signature.
      api(libs.circuit.foundation)
      implementation(libs.circuit.runtime.ui)
      // presenterOf, for the fake presenters behind previewCircuit in PreviewSupport.kt.
      implementation(libs.circuit.runtime.presenter)
      implementation(libs.circuit.codegen.annotations)

      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.compose.material3)
      implementation(libs.compose.material3.adaptive)
      implementation(libs.compose.material3.adaptive.layout)
      // Generates the Res class from src/commonMain/composeResources — the multiplatform
      // replacement for the Android res/ directory this module used to have.
      implementation(libs.compose.components.resources)
      implementation(libs.compose.ui.tooling.preview)
    }
  }
}

// Android Studio's preview renderer looks up androidx.compose.ui.tooling.ComposeViewAdapter on the
// *module's own* runtime classpath, so the annotations alone are not enough to draw anything.
// androidRuntimeClasspath is resolvable-only and is not one of the published variants, which makes
// it the right place for a dependency that must exist locally and never reach :app. The AGP KMP
// library plugin has no build types, so there is no debugImplementation to scope this with.
dependencies { androidRuntimeClasspath(libs.compose.ui.tooling) }
