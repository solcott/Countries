plugins { id("kmp-library") }

kotlin {
  sourceSets {
    // `api`, not `implementation`: ContentState.origin and LoadStatus.Failed.error are Origin and
    // DataError, so every consumer of this module sees them.
    commonMain.dependencies { api(project(":dataresult")) }
  }
}
