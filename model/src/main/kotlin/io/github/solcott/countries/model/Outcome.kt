package io.github.solcott.countries.model

/**
 * The result of a single data emission: either [Data] carrying a value, or an [Error] carrying a
 * typed [DataError].
 *
 * Loading is deliberately *not* modeled here. It is a property of an in-flight request, tracked by
 * the consumer, rather than of a settled result — so a source may keep emitting [Outcome]s (cache,
 * then network) while the consumer decides when the request is done.
 *
 * Every case records the [Origin] it came from, letting callers distinguish cached data from fresh
 * network data.
 */
sealed interface Outcome<out T> {
  data class Data<out T>(val data: T, val origin: Origin) : Outcome<T>

  data class Error(val cause: DataError, val origin: Origin) : Outcome<Nothing>
}
