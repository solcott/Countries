plugins {
  id("app")
  id("org.jetbrains.kotlin.plugin.compose")
  alias(libs.plugins.metro)
  id("formatting")
}

android {
  namespace = "io.github.solcott.countries"

  defaultConfig {
    applicationId = "io.github.solcott.countries"
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes { release { isMinifyEnabled = false } }

  buildFeatures { compose = true }
}

dependencies {
  // Only :app may depend on every other module. See AGENTS.md.
  implementation(project(":model"))
  implementation(project(":network"))
  implementation(project(":repository"))
  implementation(project(":presenter"))
  implementation(project(":ui"))

  implementation(libs.circuit.foundation)
  implementation(libs.apollo.runtime)

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.material)

  testImplementation(libs.junit)
}
