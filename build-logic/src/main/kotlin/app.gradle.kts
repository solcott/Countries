import io.github.solcott.countries.build.Versions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.android.application")
  id("detekt")
  id("com.squareup.sort-dependencies")
}

android {
  compileSdk { version = release(Versions.compileSdk) { minorApiLevel = Versions.compileSdkMinor } }

  defaultConfig {
    minSdk = Versions.minSdk
    targetSdk = Versions.targetSdk
  }

  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(Versions.SOURCE_COMPATIBILITY)
    targetCompatibility = JavaVersion.toVersion(Versions.TARGET_COMPATIBILITY)
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(Versions.TARGET_COMPATIBILITY.toString()))
    jvmToolchain(Versions.JVM_TOOLCHAIN)
  }
}
