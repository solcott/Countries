plugins {
  id("kmp-library")
  alias(libs.plugins.kmp.parcelize)
}

kotlin {
  sourceSets { commonMain { dependencies { implementation(libs.compose.runtime.annotations) } } }
}
