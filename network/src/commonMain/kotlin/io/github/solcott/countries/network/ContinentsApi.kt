package io.github.solcott.countries.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.countries.network.graphql.ContinentsQuery
import kotlinx.coroutines.flow.Flow

interface ContinentsApi {
  fun continentsAsFlow(): Flow<ApolloResponse<ContinentsQuery.Data>>
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class ApolloContinentsApi(private val apolloClient: ApolloClient) : ContinentsApi {
  override fun continentsAsFlow(): Flow<ApolloResponse<ContinentsQuery.Data>> {
    return apolloClient.query(ContinentsQuery()).toFlow()
  }
}
