import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins { id("dev.detekt") }

configure<DetektExtension> {
  config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
  buildUponDefaultConfig = true
  allRules = false
}

tasks.withType<Detekt> {
  reports { html.required = true }

  exclude {
    // excludes build directories
    val buildDirectory = project.layout.buildDirectory.get().asFile
    it.file.absolutePath.startsWith(buildDirectory.absolutePath) ||
      it.file.path.contains("/build/") ||
      it.file.name == "SharedBuildConfig.kt"
  }
}

dependencies {
  val composeRulesDep = versionCatalog.findLibrary("detekt.compose.rules").get()
  add("detektPlugins", composeRulesDep)
}

tasks.register("detektAll") {
  group = "verification"
  val detektTasks = tasks.withType<Detekt>().matching { it.name != "detektDevJvm" }
  dependsOn(detektTasks)
}

val Project.versionCatalog
  get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
