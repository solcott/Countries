import io.github.solcott.countries.build.Versions
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// The Apple entry point: the Kotlin half of the SwiftUI app, packaged as an XCFramework that
// `iosApp/Countries.xcodeproj` links. Named `:apple` rather than `:ios` because one framework
// serves iPhone, iPad and Mac.
//
// Deliberately NOT `id("kmp-library")`, for the same reason `:web` and `:desktop` are not: that
// convention exists for libraries. It adds android, jvm, js and wasmJs targets this module has no
// use for, and it never declares a framework binary — which is the only thing this module exists
// to produce.
//
// Unlike `:app`, `:web` and `:desktop`, this module *does* hold wiring. Those three read a
// finished graph and mount `CountriesApp`; here there is no Compose UI to mount, so the job of
// turning `CoreGraph` into something a Swift view can observe has to live somewhere. It lives
// here, not in the Xcode project, so Swift never constructs a Kotlin object graph by hand.
plugins {
  id("formatting")
  id("org.jetbrains.kotlin.multiplatform")
  // Molecule recomposes `presenter.present()`, which is a @Composable call, so this module needs
  // the Compose compiler even though it renders nothing.
  id("org.jetbrains.kotlin.plugin.compose")
  // So createGraph<CoreGraph>() resolves, exactly as in :app, :web and :desktop. The graph itself,
  // and every contribution to it, is aggregated on :shared's compile classpath — not here.
  alias(libs.plugins.metro)
}

// Matches the `import CountriesKit` in the Swift sources and the framework name the Xcode target
// links. Changing it means changing both.
val frameworkName = "CountriesKit"

kotlin {
  jvmToolchain(Versions.JVM_TOOLCHAIN)

  // SPIKE (Gate 1): generate Swift export output alongside the existing Obj-C framework, to find
  // out what it makes of this module's API before anything commits to it. Additive on purpose —
  // the framework block below still produces the binary `iosApp/` links, so nothing is broken
  // while this question is open.
  //
  // Deliberately still on Kotlin 2.4.10 with SKIE merely removed rather than on 2.4.20-Beta2:
  // Swift export has run on stable Kotlin since 2.2.20, so the export question and the beta-Kotlin
  // question are separable, and fusing them would make a failure uninterpretable.
  @OptIn(org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl::class)
  swiftExport {
    moduleName = frameworkName
    flattenPackage = "io.github.solcott.countries.apple"

    // Deliberately NO `export(project(":model"))` / `export(project(":presenter"))`, unlike the
    // framework block below.
    //
    // `export()` here does not mean the same thing it means for an Obj-C framework. There it is
    // the only way to get a module's types into the API at all, and it is not transitive. Swift
    // export instead emits every declaration *reachable* from this module's public API whether or
    // not the owning module is named — `:model` and `:presenter` still come through as their own
    // Swift modules. Naming them explicitly does something extra and unwanted: it exports each
    // module's public API *in full*, including declarations Swift never touches.
    //
    // That is not merely wasteful, it breaks the build. `Outcome<out T>` in `:model` is a generic
    // sealed interface, and 2.4.20-Beta2's new sealed-to-Swift-enum codegen emits invalid Swift
    // for it ("cannot convert value of type '…Outcome_Data' to specified type '…Outcome'"). No
    // Swift code references `Outcome`; it was breaking the build purely for being public.
  }

  // One XCFramework holding all three slices, because a plain .framework covers a single platform
  // and the Xcode target has iPhone, iPad and Mac destinations. Left dynamic (the Kotlin default);
  // going static would need SKIE's Swift bundling verified against it first.
  //
  // No iosX64 and no macosX64: `kmp-library` declares Apple Silicon only, and a slice the library
  // modules cannot build is a slice this cannot link.
  val xcf = XCFramework(frameworkName)
  listOf(iosArm64(), iosSimulatorArm64(), macosArm64()).forEach { target ->
    target.binaries.framework {
      baseName = frameworkName
      xcf.add(this)

      // The Apollo Gradle plugin adds this automatically once it sees normalized-cache-sqlite —
      // but it does that in `:network`, for `:network`'s own binaries. This framework is a
      // different binary in a module that does not apply Apollo, so the flag has to be repeated
      // here or the link fails on a wall of undefined _sqlite3_* symbols from SQLiter.
      linkerOpts("-lsqlite3")

      // Kotlin cannot infer one, because every package in this framework and its exports is a
      // Kotlin package rather than a reverse-DNS bundle identifier. Left unset it warns and falls
      // back to the bundle *name*, which collides across frameworks.
      binaryOption("bundleId", "io.github.solcott.countries.apple")
      // export() is not transitive, so every module whose types appear in the Swift API is listed
      // even though :presenter already exposes the other two as `api`. Each must also be an `api`
      // dependency below, or the framework task fails.
      export(project(":model"))
      export(project(":presenter"))
      export(libs.circuit.runtime)
      export(libs.circuit.runtime.screen)
    }
  }

  sourceSets {
    // With only Apple targets on the module, commonMain *is* appleMain — the same reasoning that
    // makes commonMain the web source set in `:web`. A src/appleMain would hold everything and
    // distinguish nothing.
    commonMain.dependencies {
      // `api`, and exported above: these carry Country, CountryDetail, ContentState, DataError,
      // the Screens and their States across the framework boundary.
      api(project(":model"))
      api(project(":presenter"))
      api(libs.circuit.runtime)
      api(libs.circuit.runtime.screen)

      // CoreGraph, and the repositories it vends. Not exported — Swift never sees the graph, only
      // what CountriesKit hands back.
      implementation(project(":shared"))
      implementation(libs.molecule.runtime)
      implementation(libs.compose.runtime)
      implementation(libs.kotlinx.coroutines.core)
    }

    // Wired up by hand rather than by `kmp-library`, which this module deliberately does not
    // apply. Tests run on iosSimulatorArm64Test and macosArm64Test, so they need Xcode.
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.turbine)
    }
  }
}
