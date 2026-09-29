import io.github.solcott.countries.build.ComposeStabilityCheckTask
import io.github.solcott.countries.build.composeReportCompileTask
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

// Turns on the Compose compiler's own metrics and reports and registers the check that reads them.
// Gated on the compiler plugin rather than applying it, the same way kmp.library reacts to
// `org.jetbrains.compose` -- the six Compose modules each declare their own alias.
//
// The destinations are always set rather than hidden behind a `-P` flag, so `composeStabilityCheck`
// is a plain `dependsOn` with nothing to remember on CI. The cost is three small text files per
// compilation.
plugins.withId("org.jetbrains.kotlin.plugin.compose") {
  // Not `build/compose`: the Compose Multiplatform plugin already unpacks the skiko runtime there.
  val composeDirectory = layout.buildDirectory.dir("reports/compose")
  val reportsDirectory = composeDirectory.map { it.dir("reports") }

  configure<ComposeCompilerGradlePluginExtension> {
    reportsDestination = reportsDirectory
    metricsDestination = composeDirectory.map { it.dir("metrics") }
  }

  // The Compose plugin hands `reportsDestination` to the compiler as an *input* option, so a
  // compile task whose reports have been deleted still counts as up to date and quietly writes
  // nothing -- the check would then fail with "no reports found" on a perfectly healthy tree.
  // Declaring the directory as an output of the one task the check reads models what the task
  // actually produces and fixes that. It buys a second thing too: after a full `build`, where
  // every target writes over the shared directory, this task is out of date again and re-runs, so
  // it stays the last writer and the check stays deterministic.
  afterEvaluate { composeReportCompileTask().configure { outputs.dir(reportsDirectory) } }

  tasks.register<ComposeStabilityCheckTask>("composeStabilityCheck") {
    group = "verification"
    description = "Fails on Compose stability regressions reported by the Compose compiler."

    // Resolved lazily: Android compilations do not exist when this plugin is applied.
    dependsOn(provider { composeReportCompileTask() })

    reports.from(
      reportsDirectory.map {
        it.asFileTree.matching { include("*-classes.txt", "*-composables.txt") }
      }
    )
    unstableClassAllowlist =
      rootProject.layout.projectDirectory.file("config/compose/unstable-classes.txt")
    unskippableComposableAllowlist =
      rootProject.layout.projectDirectory.file("config/compose/unskippable-composables.txt")
    moduleName = project.name
    summary = composeDirectory.map { it.file("stability-check.txt") }
  }
}
