package io.github.solcott.countries.model

import androidx.compose.runtime.Immutable
import io.github.solcott.kmp.parcelize.Parcelable
import io.github.solcott.kmp.parcelize.Parcelize

@Immutable @Parcelize data class Continent(val code: String, val name: String) : Parcelable
