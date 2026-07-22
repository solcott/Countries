package io.github.solcott.countries.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.countries.model.Continent
import io.github.solcott.countries.model.Response
import io.github.solcott.countries.network.ContinentsApi
import kotlinx.coroutines.flow.Flow

interface ContinentRepository {

  fun continentsAsFlow(): Flow<Response<List<Continent>>>
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
internal class ContinentRepositoryImpl(private val api: ContinentsApi) : ContinentRepository {
  override fun continentsAsFlow(): Flow<Response<List<Continent>>> {
    return api.continentsAsFlow().mapToResponse { continents.map { Continent(it.code, it.name) } }
  }
}
