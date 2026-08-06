package io.github.solcott.countries.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory

// The application Context comes from the androidx.startup initializer shipped in the
// normalized-cache-sqlite AAR, so the name-only overload is enough. The database lands in cacheDir.
internal actual fun ApolloClient.Builder.platformConfiguration(): ApolloClient.Builder =
  memoryCacheBackedBy(SqlNormalizedCacheFactory(DATABASE_NAME))
