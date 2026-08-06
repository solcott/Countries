import io.github.solcott.countries.build.Versions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("com.android.application") }

android {
  compileSdk = Versions.compileSdk

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
