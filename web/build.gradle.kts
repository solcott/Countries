import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// The browser entry point, and the js/wasmJs counterpart to `:app`. Like `:app` it holds no
// dependency wiring of its own — it reads `ComposeGraph` from `:shared-compose` and mounts
// `CountriesApp` from `:ui`.
//
// Deliberately NOT `id("kmp-library")`. That convention exists for libraries: it adds android, jvm,
// ios and macos targets this module has no use for, and it never calls `binaries.executable()`,
// which is what turns a klib into a webpack bundle.
plugins {
  id("formatting")
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.kotlin.plugin.compose")
  // Required even though every dependency is declared by catalog coordinate: this is what
  // configures skiko's npm/webpack packaging, which compose.ui pulls in on js and wasmJs.
  alias(libs.plugins.compose.multiplatform)
  // So createGraph<ComposeGraph>() resolves, exactly as in :app. The graph itself, and every
  // contribution to it, is aggregated on :shared-compose's compile classpath — not here.
  alias(libs.plugins.metro)
}

kotlin {
  // Both distributions serve the same index.html, so both bundles get the same name.
  js {
    browser { commonWebpackConfig { outputFileName = "countries.js" } }
    binaries.executable()
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser { commonWebpackConfig { outputFileName = "countries.js" } }
    binaries.executable()
  }

  sourceSets {
    // With only js and wasmJs on the module, commonMain *is* the web source set: the metadata
    // compilation resolves against kotlinx-browser and org.w3c.dom, which is how Compose
    // Multiplatform writes its own ComposeViewport in a shared webMain. No expect/actual needed
    // for the history binding.
    commonMain.dependencies {
      implementation(project(":shared-compose"))
      implementation(project(":ui"))
      implementation(project(":presenter"))
      implementation(libs.circuit.foundation)
      implementation(libs.compose.runtime)
      implementation(libs.compose.ui)
      implementation(libs.kotlinx.browser)
    }

    // Wired up by hand rather than by `kmp-library`, which this module deliberately does not
    // apply. Tests run on jsBrowserTest and wasmJsBrowserTest, so they need Chrome.
    commonTest.dependencies { implementation(kotlin("test")) }

    // Build-time only, and needed by webpack.config.d/sqljs.js. devNpm() is available only to
    // JS-family source sets, so it is declared per target the same way :network declares the
    // SQL.js packages themselves.
    jsMain.dependencies { implementation(devNpm("copy-webpack-plugin", "9.1.0")) }
    wasmJsMain.dependencies { implementation(devNpm("copy-webpack-plugin", "9.1.0")) }
  }
}
