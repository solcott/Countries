package io.github.solcott.countries.presenter

import io.github.solcott.countries.dataresult.Outcome
import io.github.solcott.countries.uistate.ContentState
import io.github.solcott.countries.uistate.LoadStatus

/**
 * Folds a single [Outcome] into the running state.
 *
 * A [Outcome.Data] settles the state: it replaces the held value, records its origin, and moves
 * [ContentState.status] to [LoadStatus.Idle] (also clearing a prior failure). Settling here —
 * rather than on flow completion — is what makes a continuously emitting source (cache watcher,
 * subscription, SSE) work: each pushed value is authoritative on arrival. An [Outcome.Error]
 * records the failure while the previously loaded data is kept on screen.
 */
fun <T> ContentState<T>.applyEmission(outcome: Outcome<T>): ContentState<T> =
  when (outcome) {
    is Outcome.Data -> copy(data = outcome.data, origin = outcome.origin, status = LoadStatus.Idle)
    is Outcome.Error -> copy(status = LoadStatus.Failed(outcome.cause))
  }
