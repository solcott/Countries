plugins {
  id("kmp-library")
  alias(libs.plugins.apollo)
  alias(libs.plugins.metro)
}

apollo {
  service("countries") {
    packageName.set("io.github.solcott.countries.network.graphql")
    introspection {
      endpointUrl.set("https://countries.trevorblades.com/")
      schemaFile.set(file("src/commonMain/graphql/schema.graphqls"))
    }
    plugin(
      "com.apollographql.cache:normalized-cache-apollo-compiler-plugin:${libs.versions.apollo.normalized.cache.get()}"
    )
    pluginArgument("com.apollographql.cache.packageName", packageName.get())
  }
}

// The Apollo plugin detects the Kotlin Multiplatform plugin on its own: it reads operations from
// src/commonMain/graphql and adds the generated sources to commonMain. It also links -lsqlite3
// for the native targets once it sees the normalized-cache-sqlite dependency.
kotlin {
  sourceSets {
    commonMain.dependencies {
      // `api` so consumers see model types in this module's public signatures.
      api(project(":model"))
      api(libs.apollo.api)
      implementation(libs.apollo.normalized.cache)
      implementation(libs.apollo.normalized.cache.sqlite)
      implementation(libs.apollo.runtime)
      implementation(libs.kotlinx.coroutines.core)
    }

    // SQLDelight's SQL.js worker driver backs SqlNormalizedCacheFactory on js/wasmJs. :network
    // constructs that driver itself rather than calling createDefaultWebWorkerDriver(), so it can
    // point it at a worker that persists — see NetworkProviders.web.kt and npm/ below.
    webMain.dependencies {
      implementation(libs.sqldelight.web.worker.driver)
      implementation(libs.kotlinx.browser)
    }

    // npm() is only available to JS-family source sets, so these are declared per target rather
    // than on webMain. `sql.js` is pinned to what the SQLDelight release expects.
    //
    // The worker is a local package rather than a loose file because `new Worker(new URL(…))` has
    // to resolve at bundle time, and a bare specifier resolved out of node_modules is the only
    // shape that works from a *library* module — it is also exactly how the reference worker
    // ships.
    jsMain.dependencies {
      implementation(npm("countries-sqljs-idb-worker", file("npm/countries-sqljs-idb-worker")))
      implementation(npm("sql.js", "1.8.0"))
    }
    wasmJsMain.dependencies {
      implementation(npm("countries-sqljs-idb-worker", file("npm/countries-sqljs-idb-worker")))
      implementation(npm("sql.js", "1.8.0"))
    }
  }
}

metro { generateContributionProviders = true }
