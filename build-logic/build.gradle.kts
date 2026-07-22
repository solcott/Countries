import dev.detekt.gradle.Detekt
import org.gradle.kotlin.dsl.withType

plugins {
  `kotlin-dsl`
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.detekt)
  alias(libs.plugins.dependency.sorter)
}

dependencies {
  implementation(libs.android.gradle.plugin)
  implementation(libs.kotlin.gradle.plugin)

  compileOnly(libs.plugins.dependency.sorter.toDep())
  compileOnly(libs.plugins.detekt.toDep())
  compileOnly(libs.plugins.ktfmt.toDep())

  detektPlugins(
    libs.detekt.compose.rules
  ) // required in order to use same detekt.yml as main project
}

kotlin { jvmToolchain(25) }

tasks.validatePlugins { enableStricterValidation = true }

detekt {
  config.setFrom(files("$rootDir/../detekt/detekt.yml"))
  buildUponDefaultConfig = true
  allRules = false
}

tasks.withType<Detekt> {
  reports { html.required = true }
  exclude("**/build/**")
  exclude("**/generated/**")
}

ktfmt {
  googleStyle()
  removeUnusedImports = true
}

fun Provider<PluginDependency>.toDep() = map {
  "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}
