package io.github.solcott.countries.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.solcott.countries.model.DataError

/** Resolves a transport-agnostic [DataError] into a localized, user-facing message. */
@Composable
fun DataError.toUserMessage(): String =
    when (this) {
      DataError.Network -> stringResource(R.string.error_offline)
      is DataError.Http -> stringResource(R.string.error_http, code)
      is DataError.Api -> stringResource(R.string.error_api)
      DataError.Serialization -> stringResource(R.string.error_data)
      is DataError.Unknown -> stringResource(R.string.unknown_error_occurred)
    }
