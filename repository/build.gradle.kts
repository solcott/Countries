plugins {
  id("kmp-library")
  alias(libs.plugins.metro)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":dataresult"))
      api(project(":model"))
      implementation(project(":network"))
      // Read-only access to Apollo's per-response cache metadata (isFromCache) for Origin mapping.
      implementation(libs.apollo.normalized.cache)
      // `implementation`: Logger never appears in this module's public signatures.
      implementation(libs.kermit)
    }

    commonTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
      // TestLogWriter, so the mappers' logging is asserted rather than assumed.
      implementation(libs.kermit.test)
    }
  }
}
