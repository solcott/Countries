plugins {
  id("library")
  alias(libs.plugins.metro)
}

dependencies {
  api(project(":model"))
  implementation(project(":network"))

  testImplementation(libs.junit)
}
