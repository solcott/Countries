package io.github.solcott.countries

import android.app.Application
import dev.zacsweers.metro.createGraph
import io.github.solcott.countries.shared.compose.ComposeGraph

class CountriesApplication : Application() {
  /**
   * The graph itself lives in `:shared-compose` so every Compose app — this one, and the CMP iOS,
   * desktop and web apps to come — shares one declaration. This module only hosts the Android entry
   * point.
   */
  val graph: ComposeGraph by lazy { createGraph<ComposeGraph>() }
}
