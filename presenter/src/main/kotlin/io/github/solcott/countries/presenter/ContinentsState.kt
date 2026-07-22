package io.github.solcott.countries.presenter

import io.github.solcott.countries.model.Continent

data class ContinentsState(
    override val loading: Boolean = true,
    override val data: List<Continent> = emptyList(),
    override val error: Boolean = false,
    override val errorMessage: String? = null,
) : LoadState<List<Continent>>