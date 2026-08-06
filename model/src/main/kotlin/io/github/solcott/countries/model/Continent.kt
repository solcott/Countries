package io.github.solcott.countries.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize data class Continent(val code: String, val name: String) : Parcelable
