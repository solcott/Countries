package io.github.solcott.countries.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.solcott.countries.network.graphql.cache.Cache.cache

private const val COUNTRIES_ENDPOINT = "https://countries.trevorblades.com/"

@ContributesTo(AppScope::class)
interface NetworkProviders {
  @Provides
  @SingleIn(AppScope::class)
  fun provideApolloClient(): ApolloClient {
      val sqlNormalizedCacheFactory = SqlNormalizedCacheFactory("countries.db")
      val cacheFactory = MemoryCacheFactory(maxSizeBytes = 10 * 1024 * 1024).chain(sqlNormalizedCacheFactory)
      return ApolloClient.Builder()
          .serverUrl(COUNTRIES_ENDPOINT)
          .cache(cacheFactory)
          .build()
  }

}
