package io.github.solcott.countries.presenter

import io.github.solcott.countries.model.Country

data class CountriesState(
    override val loading: Boolean = true,
    override val data: List<Country> = emptyList(),
    override val error: Boolean = false,
    override val errorMessage: String? = null,
) : LoadState<List<Country>>