package io.github.solcott.countries

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import io.github.solcott.countries.presenter.CountryListScreen
import io.github.solcott.countries.ui.theme.AppTheme

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val circuit = (application as CountriesApplication).graph.circuit

    setContent {
          AppTheme {
        val backStack = rememberSaveableBackStack(root = CountryListScreen)
        val navigator = rememberCircuitNavigator(backStack)
        CircuitCompositionLocals(circuit) {
          NavigableCircuitContent(
              navigator = navigator,
              backStack = backStack,
              Modifier.fillMaxSize(),
          )
        }
      }
    }
  }
}
