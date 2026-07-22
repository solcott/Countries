plugins {
  id("library")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.parcelize")
  alias(libs.plugins.metro)
  alias(libs.plugins.redacted)
}

android {
  buildFeatures { compose = true }
  // Compose/Circuit touch android.util.Log, which is a stub in JVM unit tests.
  testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
  api(project(":model"))
  api(libs.circuit.runtime)
  api(libs.circuit.runtime.presenter)
  implementation(libs.circuit.codegen.annotations)
  implementation(libs.circuit.retained)
  implementation(project(":repository"))
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui)

  // Presenter tests fake CountriesApi and wrap it in the real (pass-through) repository.
  testImplementation(project(":network"))
  testImplementation(libs.junit)
  testImplementation(libs.turbine)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.circuit.test)
}
