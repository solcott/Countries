import io.github.solcott.countries.build.Versions
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// KMP counterpart to `library.gradle.kts`. AGP 9 dropped KMP support from `com.android.library`,
// so the Android target comes from `com.android.kotlin.multiplatform.library` and is configured
// through an `android { }` block nested inside `kotlin { }` rather than a top-level extension.
plugins {
  id("formatting")
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

// Captured here rather than inline: inside `kotlin { android { } }`, `name` resolves to the
// Android target, not the project.
val moduleNamespace = "io.github.solcott.countries.${project.name.replace('-', '.')}"
val jvmBytecodeTarget = JvmTarget.fromTarget(Versions.TARGET_COMPATIBILITY.toString())

kotlin {
  // Applied implicitly by KGP, but stated here because modules depend on the intermediate source
  // sets it creates: `appleMain` (ios + macos), `webMain` (js + wasmJs), `nativeMain`.
  applyDefaultHierarchyTemplate()

  jvmToolchain(Versions.JVM_TOOLCHAIN)

  android {
    namespace = moduleNamespace
    compileSdk = Versions.compileSdk
    minSdk = Versions.minSdk
    compilerOptions { jvmTarget.set(jvmBytecodeTarget) }
    // The KMP Android plugin disables tests by default; opt back in so `androidHostTest` exists.
    withHostTestBuilder {}.configure {}
  }

  jvm { compilerOptions { jvmTarget.set(jvmBytecodeTarget) } }

  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  js {
    browser()
    nodejs()
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    nodejs()
  }

  sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
}
