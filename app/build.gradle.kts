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
  // The Metro graph and every module behind it come from :shared-compose. This module is just the
  // Android entry point — an Activity, a theme, and a manifest.
  implementation(project(":shared-compose"))

  implementation(libs.circuit.foundation)

  // Aligns every androidx.compose.* artifact on 1.12.0. Without it `foundation` and `animation`,
  // which nothing declares, drift to whatever material3 and Compose Multiplatform ask for.
  implementation(platform(libs.androidx.compose.bom))

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
