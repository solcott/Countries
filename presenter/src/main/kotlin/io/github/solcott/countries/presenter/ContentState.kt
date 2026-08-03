package io.github.solcott.countries.presenter

import io.github.solcott.countries.model.DataError
import io.github.solcott.countries.model.Origin
import io.github.solcott.countries.model.Outcome

/**
 * View state for a piece of content backed by a data source.
 *
 * [data] is always present so the UI can keep showing the last known value while a refresh is in
 * flight (stale-while-revalidate). [origin] records where that value came from, and [status] tracks
 * request activity independently — together they let the UI show, for example, cached data with an
 * "updating" indicator.
 *
 * The model is agnostic to whether the source completes. A one-shot query, a cache watcher that
 * re-emits after local writes, and a subscription/SSE stream are all handled the same way: fold each
 * emission with [applyEmission], which settles [status] on every value rather than waiting for the
 * flow to end. That is what lets a never-completing stream still reach a settled, non-loading state.
 */
data class ContentState<T>(
    val data: T,
    val origin: Origin? = null,
    val status: LoadStatus = LoadStatus.Loading,
)

/**
 * Request activity for a [ContentState].
 * - [Loading] — a request or refresh is in flight; makes no assumption about where it will be served
 *   from (a fetch-policy detail the consumer must not depend on).
 * - [Idle] — settled; the held value is current. A live source may still replace it later with
 *   another emission, silently, without returning to [Loading].
 * - [Failed] — the most recent request failed; any previously loaded [ContentState.data] is kept,
 *   and a live source may recover by emitting again.
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
 * Folds a single [Outcome] into the running state.
 *
 * A [Outcome.Data] settles the state: it replaces the held value, records its origin, and moves
 * [status] to [LoadStatus.Idle] (also clearing a prior failure). Settling here — rather than on flow
 * completion — is what makes a continuously emitting source (cache watcher, subscription, SSE) work:
 * each pushed value is authoritative on arrival. An [Outcome.Error] records the failure while the
 * previously loaded data is kept on screen.
 */
fun <T> ContentState<T>.applyEmission(outcome: Outcome<T>): ContentState<T> =
    when (outcome) {
      is Outcome.Data ->
          copy(data = outcome.data, origin = outcome.origin, status = LoadStatus.Idle)
      is Outcome.Error -> copy(status = LoadStatus.Failed(outcome.cause))
    }

/**
 * Marks a fresh request as in flight while keeping any data already on screen. Call this when
 * *intentionally* re-fetching (a filter change, a retry, pull-to-refresh) so the UI can show a
 * refresh indicator over the current content; the next emission settles it via [applyEmission].
 */
fun <T> ContentState<T>.reloading(): ContentState<T> = copy(status = LoadStatus.Loading)

/**
 * Settles a still-loading request to [LoadStatus.Idle]. Used as a safety net for a source that
 * completes without emitting (so the spinner never hangs); a source that emits settles via
 * [applyEmission] instead, and a never-completing source never needs this.
 */
fun <T> ContentState<T>.settled(): ContentState<T> =
    if (status is LoadStatus.Loading) copy(status = LoadStatus.Idle) else this
