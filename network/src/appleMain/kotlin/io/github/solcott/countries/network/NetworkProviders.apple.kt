package io.github.solcott.countries.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory

// Stored under "Application Support/databases", Apollo's Apple default for the name-only overload.
internal actual fun ApolloClient.Builder.platformConfiguration(): ApolloClient.Builder =
  memoryCacheBackedBy(SqlNormalizedCacheFactory(DATABASE_NAME))
