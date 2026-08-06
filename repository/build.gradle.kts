plugins {
  id("library")
  alias(libs.plugins.metro)
}

dependencies {
  api(project(":model"))
  implementation(project(":network"))
  // Read-only access to Apollo's per-response cache metadata (isFromCache) for Origin mapping.
  implementation(libs.apollo.normalized.cache)

  testImplementation(libs.junit)
}
