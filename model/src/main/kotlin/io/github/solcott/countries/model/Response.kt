package io.github.solcott.countries.model

/**
 * A sealed class representing the various states of a data request or operation.
 *
 * This is pretty basic and could be more robust.  Add support for better error responses.  For example device offline, http errors, no data found, etc
 *
 * Also, possibly add source of response (network, cache)
 */
sealed class Response<out T> {
  class Loading<T> : Response<T>()

  data class Data<T>(val data: T) : Response<T>()

  data class Error<T>(val message: String) : Response<T>()

  val isLoading: Boolean = this is Loading
  val isError: Boolean = this is Error
}
