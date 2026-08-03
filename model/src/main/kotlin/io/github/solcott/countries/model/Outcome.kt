package io.github.solcott.countries.model

/**
 * A single emission from a data source: either [Data] carrying a value, or an [Error] carrying a
 * typed [DataError].
 *
 * An `Outcome` describes *one* value in what may be an unbounded stream. A source can keep emitting
 * over time — cache then network, a cache watcher re-emitting after a local write, or a
 * subscription/SSE stream pushing server-driven changes — and each change is just another `Outcome`.
 * Consumers fold successive emissions into their own state rather than treating any one as terminal.
 *
 * Loading is deliberately *not* modeled here: it is a property of an in-flight request tracked by
 * the consumer, not of a settled value.
 *
 * Every case records the [Origin] it came from, letting callers distinguish cached data from fresh
 * network data.
 */
sealed interface Outcome<out T> {
  data class Data<out T>(val data: T, val origin: Origin) : Outcome<T>

  data class Error(val cause: DataError, val origin: Origin) : Outcome<Nothing>
}
