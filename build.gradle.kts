import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.apollo) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.kmp.parcelize) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Pins the Gradle daemon's JVM. `./gradlew updateDaemonJvm` writes the criteria to
// gradle/gradle-daemon-jvm.properties; the foojay resolver auto-downloads a matching JDK.
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(25)
    vendor.set(JvmVendorSpec.AMAZON)
}

// On CI, run the webpack and karma tasks one at a time. Each starts a Node process sized for a
// whole Compose bundle, and karma adds a Chrome on top. `build` has seven modules with web
// executables, so under org.gradle.parallel they overlap alongside the Gradle and Kotlin daemons,
// exhaust ubuntu-latest's 16 GB, and the runner is killed: the job ends as "The operation was
// canceled" or "The runner has received a shutdown signal", with no Gradle error at all.
// Locally there is enough memory, so they stay parallel.
abstract class WebBundleTaskLimiter : BuildService<BuildServiceParameters.None>

val webBundleTaskLimiter =
    gradle.sharedServices.registerIfAbsent("webBundleTaskLimiter", WebBundleTaskLimiter::class) {
        if (providers.environmentVariable("CI").isPresent) maxParallelUsages = 1
    }

subprojects {
    tasks.withType<KotlinWebpack>().configureEach { usesService(webBundleTaskLimiter) }
    tasks.withType<KotlinJsTest>().configureEach { usesService(webBundleTaskLimiter) }
}
