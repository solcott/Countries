plugins {
  id("kmp-library")
  id("org.jetbrains.kotlin.plugin.compose")
  // Not for the `compose.*` dependency accessors — dependencies are declared by coordinate below.
  // This plugin configures skiko's npm/webpack packaging, which compose.foundation pulls in on
  // js and wasmJs.
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kmp.parcelize)
  alias(libs.plugins.metro)
  alias(libs.plugins.redacted)
}

kotlin {
  // Compose Multiplatform 1.12 fails the build (checkComposeUiTestConfigurationFor{Js,WasmJs}) for
  // any module whose *test* compilation reaches skiko but declares no executable binary, because
  // the browser test bundle would otherwise have no skiko runtime to load. compose.foundation
  // pulls compose.ui pulls skiko, so this module qualifies even though its tests only drive
  // presenters and never render. There is no property to opt out of the check.
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
      api(project(":dataresult"))
      api(project(":model"))
      api(project(":uistate"))
      api(libs.circuit.runtime)
      api(libs.circuit.runtime.presenter)
      // `api`: SubScreen, SubCircuitUiState and SubCircuitOuterEvent are all supertypes in
      // SearchAndFilterScreen's public API.
      api(libs.circuitx.subcircuit)
      implementation(project(":repository"))
      implementation(libs.circuit.codegen.annotations)
      implementation(libs.circuit.retained)

      // androidx.compose.runtime is already multiplatform, so `compose.runtime` here is a thin
      // alias onto it. foundation is not, hence the Compose Multiplatform build — it is what
      // provides TextFieldState.
      implementation(libs.compose.runtime)
      implementation(libs.compose.runtime.saveable)
      implementation(libs.compose.foundation)
      implementation(libs.androidx.compose.runtime.retain)
    }

    commonTest.dependencies {
      implementation(libs.circuit.test)
      implementation(libs.circuitx.subcircuit.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.turbine)
    }
  }
}
