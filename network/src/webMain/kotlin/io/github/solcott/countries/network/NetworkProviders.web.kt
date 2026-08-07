package io.github.solcott.countries.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory

// Backed by SQLDelight's SQL.js web-worker driver — see the npm dependencies in build.gradle.kts.
// [DATABASE_NAME] is deliberately not passed: the name is ignored on this platform because the
// worker owns storage.
internal actual fun ApolloClient.Builder.platformConfiguration(): ApolloClient.Builder =
  memoryCacheBackedBy(SqlNormalizedCacheFactory())
