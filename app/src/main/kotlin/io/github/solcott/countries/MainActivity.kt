package io.github.solcott.countries

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import io.github.solcott.countries.ui.CountriesApp

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val graph = (application as CountriesApplication).graph

    // Popping past the root screen is per-platform, and Circuit's common rememberCircuitNavigator
    // has no default for it — on Android it means leaving the app.
    setContent { CountriesApp(graph.circuit, graph.subCircuit, onRootPop = { finish() }) }
  }
}
