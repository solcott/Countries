package io.github.solcott.countries.build

object Versions {
  const val compileSdk = 37
  // Android 17 QPR's minor API level. AndroidX Compose 1.13 declares a minCompileSdk of 37.1 in its
  // AAR metadata, so `:app:checkDebugAarMetadata` fails against plain 37. Set through
  // `compileSdk { version = release(compileSdk) { minorApiLevel = compileSdkMinor } }`.
  const val compileSdkMinor = 1
  const val minSdk = 28
  const val targetSdk = 37
  const val SOURCE_COMPATIBILITY = 17
  const val TARGET_COMPATIBILITY = 17
  const val JVM_TOOLCHAIN = 17
}
