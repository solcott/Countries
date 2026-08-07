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
  sourceSets {
    commonMain.dependencies {
      api(project(":model"))
      api(libs.circuit.runtime)
      api(libs.circuit.runtime.presenter)
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
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.turbine)
    }
  }
}
