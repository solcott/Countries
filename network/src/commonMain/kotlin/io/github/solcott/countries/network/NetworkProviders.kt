package io.github.solcott.countries.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.solcott.countries.network.graphql.cache.Cache.cache

private const val COUNTRIES_ENDPOINT = "https://countries.trevorblades.com/"
private const val MEMORY_CACHE_MAX_SIZE_BYTES = 10 * 1024 * 1024

internal const val DATABASE_NAME = "countries.db"

@ContributesTo(AppScope::class)
interface NetworkProviders {
  @Provides
  @SingleIn(AppScope::class)
  fun provideApolloClient(): ApolloClient =
    ApolloClient.Builder().serverUrl(COUNTRIES_ENDPOINT).platformConfiguration().build()
}

/**
 * The per-platform half of the client configuration. The endpoint is identical everywhere, so it
 * stays above; this is where the persistent cache — and later HTTP engines or interceptors —
 * differ.
 */
internal expect fun ApolloClient.Builder.platformConfiguration(): ApolloClient.Builder

/** Two-tier cache shared by every platform: an in-memory tier in front of [persistent]. */
internal fun ApolloClient.Builder.memoryCacheBackedBy(
  persistent: NormalizedCacheFactory
): ApolloClient.Builder =
  cache(MemoryCacheFactory(maxSizeBytes = MEMORY_CACHE_MAX_SIZE_BYTES).chain(persistent))
