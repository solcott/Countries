package io.github.solcott.countries.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory

// Stored under "${user.home}/.apollo", which is Apollo's JVM default for the name-only overload.
internal actual fun ApolloClient.Builder.platformConfiguration(): ApolloClient.Builder =
  memoryCacheBackedBy(SqlNormalizedCacheFactory(DATABASE_NAME))
