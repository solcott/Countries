package io.github.solcott.countries.presenter

import io.github.solcott.countries.model.DataError
import io.github.solcott.countries.model.Origin
import io.github.solcott.countries.model.Outcome

/**
 * View state for an asynchronously loaded piece of content.
 *
 * [data] is always present so the UI can keep showing the last known value while a refresh is in
 * flight (stale-while-revalidate). [origin] records where that data came from, and [status] tracks
 * the current request independently — the two together let the UI show, for example, cached data
 * with an "updating" indicator.
 */
data class ContentState<T>(
    val data: T,
    val origin: Origin? = null,
    val status: LoadStatus = LoadStatus.Loading,
)

/**
 * The state of the request backing a [ContentState]. [Loading] means a request is in flight and
 * makes no assumption about where it will be served from — that is a fetch-policy detail the
 * consumer must not depend on.
 */
sealed interface LoadStatus {
  data object Idle : LoadStatus

  data object Loading : LoadStatus

  data class Failed(val error: DataError) : LoadStatus
}

/** True while a request is in flight. */
val ContentState<*>.isLoading: Boolean
  get() = status is LoadStatus.Loading

/** The failure of the most recent request, or null if it did not fail. */
val ContentState<*>.errorOrNull: DataError?
  get() = (status as? LoadStatus.Failed)?.error

/**
 * Folds a single [Outcome] into the running state. Data replaces the held value and records its
 * origin but leaves [status] as-is — the request is only considered settled once its flow completes
 * (see the presenters). An error is recorded while the previously loaded data is kept on screen.
 */
fun <T> ContentState<T>.applyEmission(outcome: Outcome<T>): ContentState<T> =
    when (outcome) {
      is Outcome.Data -> copy(data = outcome.data, origin = outcome.origin)
      is Outcome.Error -> copy(status = LoadStatus.Failed(outcome.cause))
    }

/** Settles a still-loading request to [LoadStatus.Idle], leaving a failed or idle status untouched. */
fun <T> ContentState<T>.settled(): ContentState<T> =
    if (status is LoadStatus.Loading) copy(status = LoadStatus.Idle) else this
