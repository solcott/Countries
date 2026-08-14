package io.github.solcott.countries.ui

import androidx.compose.runtime.Composable
import io.github.solcott.countries.dataresult.DataError
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.error_api
import io.github.solcott.countries.ui.resources.error_data
import io.github.solcott.countries.ui.resources.error_http
import io.github.solcott.countries.ui.resources.error_offline
import io.github.solcott.countries.ui.resources.unknown_error_occurred
import org.jetbrains.compose.resources.stringResource

/** Resolves a transport-agnostic [DataError] into a localized, user-facing message. */
@Composable
fun DataError.toUserMessage(): String =
  when (this) {
    DataError.Network -> stringResource(Res.string.error_offline)
    is DataError.Http -> stringResource(Res.string.error_http, code)
    is DataError.Api -> stringResource(Res.string.error_api)
    DataError.Serialization -> stringResource(Res.string.error_data)
    is DataError.Unknown -> stringResource(Res.string.unknown_error_occurred)
  }
