package io.github.solcott.countries.model

import io.github.solcott.kmp.parcelize.Parcelable
import io.github.solcott.kmp.parcelize.Parcelize

@Parcelize data class Continent(val code: String, val name: String) : Parcelable
