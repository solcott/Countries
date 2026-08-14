import io.github.solcott.countries.build.Versions
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// The desktop entry point, and the jvm counterpart to `:app` and `:web`. Like both of those it
// holds no dependency wiring of its own — it reads `ComposeGraph` from `:shared-compose` and mounts
// `CountriesApp` from `:ui`.
//
// A plain Kotlin/JVM module rather than a multiplatform one: desktop *is* the jvm target, so there
// is no second target for `kotlin { }` to hold, and `compose.desktop.application` is built around
// this shape. `:web` is multiplatform only because it has to serve js and wasmJs from one module.
plugins {
  id("formatting")
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.kotlin.plugin.compose")
  // Brings the `compose.desktop` extension — the packaging tasks and the OS-classified runtime.
  alias(libs.plugins.compose.multiplatform)
  // So createGraph<ComposeGraph>() resolves, exactly as in :app and :web. The graph itself, and
  // every contribution to it, is aggregated on :shared-compose's compile classpath — not here.
  alias(libs.plugins.metro)
}

// `Versions` reaches a module build script, not just the convention plugins: it ships in the same
// build-logic jar as the `formatting` plugin above, which puts it on this script's classpath.
kotlin { jvmToolchain(Versions.JVM_TOOLCHAIN) }

sourceSets.main {
  // `icons/` is the source of truth for both consumers: jpackage reads the three files from disk
  // (below), and the running app loads icon.png off the classpath for its window and dock icon.
  // Only the PNG is useful at runtime, so the two installer-only formats stay out of the jar.
  resources.srcDir(layout.projectDirectory.dir("icons"))
  resources.exclude("*.icns", "*.ico")
}

dependencies {
  implementation(project(":shared-compose"))
  implementation(project(":ui"))
  implementation(project(":presenter"))

  implementation(libs.circuit.foundation)
  implementation(libs.compose.runtime)
  implementation(libs.compose.ui)

  // The one place this project reaches for a `compose.*` accessor instead of a catalog coordinate.
  // It has to: skiko's runtime artifact is classified by OS *and* architecture
  // (skiko-awt-runtime-macos-arm64, …) and only this accessor picks the right one. The consequence
  // is that anything built here — including the uber jar — runs on the build host's OS only.
  implementation(compose.desktop.currentOs)

  testImplementation(kotlin("test"))
}

compose.desktop.application {
  mainClass = "io.github.solcott.countries.desktop.MainKt"

  nativeDistributions {
    targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
    packageName = "Countries"
    // jpackage requires x.y.z, and rejects a major version of 0.
    packageVersion = "1.0.0"

    // jpackage jlinks a trimmed JDK, and these are not in the default module set: java.sql and
    // jdk.unsupported for sqlite-jdbc behind the Apollo cache, java.naming and jdk.crypto.ec for
    // OkHttp's TLS. Nothing warns — `run` uses the full JDK, so a missing module surfaces only in
    // an installed build, as a crash on the first query.
    modules("java.sql", "java.naming", "jdk.crypto.ec", "jdk.unsupported")

    macOS {
      bundleID = "io.github.solcott.countries"
      iconFile.set(project.file("icons/icon.icns"))
    }
    windows { iconFile.set(project.file("icons/icon.ico")) }
    linux { iconFile.set(project.file("icons/icon.png")) }
  }
}
