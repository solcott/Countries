package io.github.solcott.countries.build

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * The one compile task whose Compose reports the stability check reads.
 *
 * `reportsDestination` is a project-level setting the Compose plugin hands to every compilation
 * verbatim -- unlike `metricsDestination`, it gets no per-target subdirectory -- and a KMP module's
 * targets share a Kotlin module name (`ui/build/classes/kotlin/jvm/main` and `.../android/main`
 * both hold `Recipes_ui.kotlin_module`). So all six targets write the same `Recipes_ui-classes.txt`
 * and the last one to run wins. Depending on exactly one compile task leaves a single writer, which
 * is what makes `composeStabilityCheck` deterministic. Which target hardly matters: stability
 * inference for `commonMain` is identical on all of them, so prefer the JVM for compile speed.
 */
internal fun Project.composeReportCompileTask(): TaskProvider<*> {
  val kotlin =
    extensions.findByName("kotlin") as? KotlinProjectExtension
      ?: error(
        "No Kotlin extension in :$name -- compose.stability needs one to find a compilation."
      )
  val targets =
    when (kotlin) {
      is KotlinMultiplatformExtension -> kotlin.targets.toList()
      is KotlinSingleTargetExtension<*> -> listOf(kotlin.target)
      else -> emptyList()
    }
  val target =
    PLATFORM_PREFERENCE.firstNotNullOfOrNull { platform ->
      targets.firstOrNull { it.platformType == platform }
    } ?: error("No Kotlin target in :$name can produce Compose reports.")
  val compilation =
    target.compilations.findByName("main")
      // KotlinAndroidTarget names its compilations after build types, so there is no "main".
      ?: target.compilations.findByName("debug")
      ?: error("Target ${target.name} in :$name has neither a main nor a debug compilation.")
  return compilation.compileTaskProvider
}

/** Metadata is deliberately absent: it compiles no bodies, so the compiler reports nothing. */
private val PLATFORM_PREFERENCE =
  listOf(
    KotlinPlatformType.jvm,
    KotlinPlatformType.androidJvm,
    KotlinPlatformType.js,
    KotlinPlatformType.wasm,
    KotlinPlatformType.native,
  )
