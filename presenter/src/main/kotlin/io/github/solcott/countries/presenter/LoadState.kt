package io.github.solcott.countries.presenter

interface LoadState<T> {
    val loading: Boolean
    val data: T
    val error: Boolean
    val errorMessage: String?
}
