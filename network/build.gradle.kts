plugins {
  id("library")
  alias(libs.plugins.apollo)
  alias(libs.plugins.metro)
}

apollo {
  service("countries") {
    packageName.set("io.github.solcott.countries.network.graphql")
    introspection {
      endpointUrl.set("https://countries.trevorblades.com/")
      schemaFile.set(file("src/main/graphql/schema.graphqls"))
    }
    plugin(
      "com.apollographql.cache:normalized-cache-apollo-compiler-plugin:${libs.versions.apollo.normalized.cache.get()}"
    )
    pluginArgument("com.apollographql.cache.packageName", packageName.get())
  }
}

dependencies {
  // `api` so consumers see model types in this module's public signatures.
  api(project(":model"))

  api(libs.apollo.api)
  implementation(libs.apollo.normalized.cache)
  implementation(libs.apollo.normalized.cache.sqlite)
  implementation(libs.apollo.runtime)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
}

metro { generateContributionProviders = true }
