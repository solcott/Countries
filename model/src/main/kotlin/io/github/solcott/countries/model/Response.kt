package io.github.solcott.countries.model

sealed class Response<out T> {
  class Loading<T> : Response<T>()

  data class Data<T>(val data: T) : Response<T>()

  data class Error<T>(val message: String) : Response<T>()

  val isLoading: Boolean = this is Loading
  val isError: Boolean = this is Error
}
