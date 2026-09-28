package io.github.solcott.countries.build

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Turns the Compose compiler's own stability reports into a pass or a fail.
 *
 * Reads the `-classes.txt` and `-composables.txt` files the compiler writes to `reportsDestination`
 * and fails on two things:
 * * a class the compiler inferred as `unstable` -- every recomposition re-runs any composable that
 *   takes one;
 * * a `restartable` composable that is not `skippable`. Strong skipping is on by default in Kotlin
 *   2.4, so what survives is genuine: inline functions, non-`Unit` returns, and
 *   `@NonSkippableComposable`.
 *
 * Both are overridable through an allowlist file, one `<module>:<SimpleName>` per line. The module
 * prefix is what lets each project's copy of this task tell its own entries from another module's.
 */
@CacheableTask
abstract class ComposeStabilityCheckTask : DefaultTask() {

  /** The `*-classes.txt` and `*-composables.txt` files written to `reportsDestination`. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val reports: ConfigurableFileCollection

  /** Classes allowed to stay unstable. */
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val unstableClassAllowlist: RegularFileProperty

  /** Restartable composables allowed to stay unskippable. */
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val unskippableComposableAllowlist: RegularFileProperty

  /** The project name, which is the prefix this task's allowlist entries must carry. */
  @get:Input abstract val moduleName: Property<String>

  @get:OutputFile abstract val summary: RegularFileProperty

  @TaskAction
  fun check() {
    val reportFiles = reports.files.filter { it.isFile }
    if (reportFiles.isEmpty()) {
      throw GradleException(
        "No Compose reports found for :${moduleName.get()}. The compiler writes them only when " +
          "composeCompiler.reportsDestination is set -- check the compose.stability convention " +
          "plugin is applied to this project."
      )
    }

    val unstableClasses =
      reportFiles.filter { it.name.endsWith(CLASSES_SUFFIX) }.flatMap(::parseUnstableClasses)
    val unskippable =
      reportFiles
        .filter { it.name.endsWith(COMPOSABLES_SUFFIX) }
        .flatMap(::parseUnskippableComposables)

    val allowedClasses = readAllowlist(unstableClassAllowlist.orNull?.asFile)
    val allowedComposables = readAllowlist(unskippableComposableAllowlist.orNull?.asFile)

    warnAboutStaleEntries(allowedClasses, unstableClasses, unstableClassAllowlist.orNull?.asFile)
    warnAboutStaleEntries(
      allowedComposables,
      unskippable,
      unskippableComposableAllowlist.orNull?.asFile,
    )

    val classFailures = unstableClasses.filterNot { it.name in allowedClasses }
    val composableFailures = unskippable.filterNot { it.name in allowedComposables }

    writeSummary(reportFiles, unstableClasses, unskippable, classFailures, composableFailures)

    if (classFailures.isNotEmpty() || composableFailures.isNotEmpty()) {
      throw GradleException(
        buildFailureMessage(classFailures, composableFailures).also { logger.error(it) }
      )
    }
  }

  private fun buildFailureMessage(
    classFailures: List<Finding>,
    composableFailures: List<Finding>,
  ): String = buildString {
    val module = moduleName.get()
    appendLine("Compose stability check failed for :$module.")
    if (classFailures.isNotEmpty()) {
      appendLine()
      appendLine("  Unstable classes (${classFailures.size}):")
      classFailures.forEach { appendLine("    ${it.name}  [${it.report.name}]") }
      appendLine(
        "  Make every property a val of a stable type, or annotate the class @Immutable " +
          "(nothing ever changes) or @Stable (mutable properties are backed by snapshot state)."
      )
      appendLine("  To accept one, add to ${allowlistPath(unstableClassAllowlist.orNull?.asFile)}:")
      classFailures.forEach { appendLine("    $module:${it.name}") }
    }
    if (composableFailures.isNotEmpty()) {
      appendLine()
      appendLine("  Restartable but not skippable (${composableFailures.size}):")
      composableFailures.forEach { appendLine("    ${it.name}  [${it.report.name}]") }
      appendLine(
        "  Every unskippable composable re-runs on each recomposition of its parent. Make its " +
          "parameters stable, or accept it below with a comment saying why."
      )
      appendLine(
        "  To accept one, add to ${allowlistPath(unskippableComposableAllowlist.orNull?.asFile)}:"
      )
      composableFailures.forEach { appendLine("    $module:${it.name}") }
    }
  }

  private fun warnAboutStaleEntries(allowed: Set<String>, found: List<Finding>, file: File?) {
    // Only ever a warning: the reports cover one target, so an entry for a class declared in, say,
    // iosMain legitimately goes unmatched when the check reads the JVM report.
    val foundNames = found.mapTo(mutableSetOf()) { it.name }
    val stale = allowed - foundNames
    if (stale.isNotEmpty()) {
      logger.warn(
        "Compose stability allowlist for :${moduleName.get()} has ${stale.size} entries that no " +
          "longer appear in the report (${stale.sorted().joinToString()}). Remove them from " +
          "${allowlistPath(file)} unless they are declared in a source set this target does not " +
          "compile."
      )
    }
  }

  private fun writeSummary(
    reportFiles: List<File>,
    unstableClasses: List<Finding>,
    unskippable: List<Finding>,
    classFailures: List<Finding>,
    composableFailures: List<Finding>,
  ) {
    val out = summary.get().asFile
    out.parentFile.mkdirs()
    out.writeText(
      buildString {
        appendLine("Compose stability -- :${moduleName.get()}")
        appendLine("reports read: ${reportFiles.joinToString { it.name }}")
        appendLine(
          "unstable classes: ${unstableClasses.size} (${classFailures.size} not allowlisted)"
        )
        unstableClasses.forEach { appendLine("  ${it.name}") }
        appendLine(
          "restartable, not skippable: ${unskippable.size} " +
            "(${composableFailures.size} not allowlisted)"
        )
        unskippable.forEach { appendLine("  ${it.name}") }
      }
    )
  }

  /**
   * Entries for this module, with the `<module>:` prefix stripped. Others are another's problem.
   */
  private fun readAllowlist(file: File?): Set<String> {
    if (file == null || !file.isFile) return emptySet()
    val prefix = "${moduleName.get()}:"
    return file
      .readLines()
      .map { it.substringBefore('#').trim() }
      .filter { it.startsWith(prefix) }
      .mapTo(mutableSetOf()) { it.removePrefix(prefix).trim() }
  }

  private fun parseUnstableClasses(report: File): List<Finding> =
    report.readLines().mapNotNull { line ->
      // Entries start at column 0; properties inside a class body are indented.
      UNSTABLE_CLASS.find(line)?.let { Finding(it.groupValues[1], report) }
    }

  private fun parseUnskippableComposables(report: File): List<Finding> =
    report.readLines().mapNotNull { line ->
      val match = COMPOSABLE.find(line) ?: return@mapNotNull null
      val flags = match.groupValues[1].trim().split(WHITESPACE)
      // `runtime`/`readonly`/inline functions are not restartable and cost nothing to skip.
      if (RESTARTABLE !in flags || SKIPPABLE in flags) return@mapNotNull null
      Finding(match.groupValues[2], report)
    }

  private fun allowlistPath(file: File?): String = file?.invariantSeparatorsPath ?: "the allowlist"

  private data class Finding(val name: String, val report: File)

  private companion object {
    const val CLASSES_SUFFIX = "-classes.txt"
    const val COMPOSABLES_SUFFIX = "-composables.txt"
    const val RESTARTABLE = "restartable"
    const val SKIPPABLE = "skippable"

    val UNSTABLE_CLASS = Regex("""^unstable class ([^\s{]+)""")
    // e.g. `restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun RecipeGrid(`.
    // Names come out fully qualified and may carry angle brackets -- a property getter reads
    // `fun com.scottolcott.recipe.domain.<get-isCupertino>()` -- so the optional type-parameter
    // group excludes `-` rather than matching any `<...>`, which would swallow those.
    val COMPOSABLE =
      Regex("""^([a-z ]*(?:scheme\("[^"]*"\)\s*)?)fun\s+(?:<[\w, ]+>\s+)?([^(\s]+)\(""")
    val WHITESPACE = Regex("""\s+""")
  }
}
