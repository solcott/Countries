plugins {
  id("kmp-library")
  alias(libs.plugins.metro)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(libs.dataresult)
      api(project(":model"))
      implementation(project(":network"))
      // The ApolloResponse -> Outcome mapping that used to be the top half of Mappers.kt.
      // `implementation`: nothing from it appears in this module's public signatures, which now
      // speak only Outcome and model types.
      implementation(libs.dataresultApollo)
      // `implementation`: Logger never appears in this module's public signatures.
      implementation(libs.kermit)
    }

    commonTest.dependencies {
      // CacheInfo, which is what `isFromCache` reads -- the tests build responses with it rather
      // than standing up a real normalized cache.
      implementation(libs.apollo.normalized.cache)
      implementation(libs.kotlinx.coroutines.test)
      // TestLogWriter, so the mappers' logging is asserted rather than assumed.
      implementation(libs.kermit.test)
    }
  }
}
