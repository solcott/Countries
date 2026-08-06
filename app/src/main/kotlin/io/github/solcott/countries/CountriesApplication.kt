package io.github.solcott.countries

import android.app.Application
import dev.zacsweers.metro.createGraph

class CountriesApplication : Application() {
  val graph: AppGraph by lazy { createGraph<AppGraph>() }
}
