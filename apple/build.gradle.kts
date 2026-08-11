import io.github.solcott.countries.build.Versions

// The Apple entry point: the Kotlin half of the SwiftUI app, exported to Swift and linked by
// `iosApp/Countries.xcodeproj`. Named `:apple` rather than `:ios` because one export serves
// iPhone, iPad and Mac.
//
// Deliberately NOT `id("kmp-library")`, for the same reason `:web` and `:desktop` are not: that
// convention exists for libraries. It adds android, jvm, js and wasmJs targets this module has no
// use for, and it never sets up an Apple binary — which is the only thing this module exists to
// produce.
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

// Matches the `import CountriesKit` in the Swift sources. Changing it means changing both.
val frameworkName = "CountriesKit"

kotlin {
  jvmToolchain(Versions.JVM_TOOLCHAIN)

  // Swift export replaces SKIE and the Obj-C framework both. It is Alpha, and this module is where
  // the project finds out what that costs.
  @OptIn(org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl::class)
  swiftExport {
    moduleName = frameworkName
    flattenPackage = "io.github.solcott.countries.apple"

    // Deliberately NO `export(project(":model"))` / `export(project(":presenter"))`, unlike the
    // `export()` an Obj-C framework binary takes.
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

  // No `binaries.framework`, and no XCFramework. Swift export does not produce a framework at all:
  // `embedSwiftExportForXcode` emits Swift source, builds it as a synthetic SPM package, and copies
  // a static `libCountriesKit.a` plus the .swiftmodule interfaces into $BUILT_PRODUCTS_DIR. There
  // is
  // nothing to embed, nothing to sign, and no bundle — so `binaryOption("bundleId", …)` went with
  // it.
  //
  // `linkerOpts("-lsqlite3")` went too, and its job moved to the Xcode target's OTHER_LDFLAGS. A
  // Kotlin/Native static library records no linker options, so SQLiter's _sqlite3_* symbols now
  // resolve when the app links rather than when the framework did.
  //
  // No iosX64 and no macosX64: `kmp-library` declares Apple Silicon only, and a slice the library
  // modules cannot build is a slice this cannot link.
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  sourceSets {
    // With only Apple targets on the module, commonMain *is* appleMain — the same reasoning that
    // makes commonMain the web source set in `:web`. A src/appleMain would hold everything and
    // distinguish nothing.
    commonMain.dependencies {
      // `api` because Swift export emits everything reachable from this module's public API, and
      // :model's data classes and :presenter's LoadStatus are reachable through the facade.
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
