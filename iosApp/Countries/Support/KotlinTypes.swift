import CountriesKit
import CountriesModel
import CountriesPresenter
import ExportedKotlinPackages

/// Short Swift names for the Kotlin types this app uses.
///
/// Swift export nests every exported declaration under its Kotlin package —
/// `ExportedKotlinPackages.io.github.solcott.countries.model.Country` — and `flattenPackage` is what
/// normally collapses that back to `Country`. `:apple`'s own types get it from the `swiftExport`
/// block, but `:model` and `:presenter` cannot: `flattenPackage` is only configurable inside an
/// `export(project(…))`, and naming those modules there exports their public API *in full*, which
/// drags in `Outcome<out T>` — a generic sealed interface no Swift code touches and one that
/// 2.4.20-Beta2's sealed-enum codegen cannot compile. See `apple/build.gradle.kts`.
///
/// So the flattening happens here instead, deliberately and for exactly the types that cross. It
/// costs this file; it buys an export surface with no Compose in it and no dead generic sealed
/// types breaking the build.
///
/// Under SKIE none of this existed, because Obj-C interop flattened everything into one namespace
/// by mangling — `DataErrorHttp`, `CountryListScreenEventRetry`. That is the tradeoff in miniature:
/// Swift export keeps the structure and asks you to name it.
typealias Continent = ExportedKotlinPackages.io.github.solcott.countries.model.Continent
typealias Country = ExportedKotlinPackages.io.github.solcott.countries.model.Country
typealias CountryDetail = ExportedKotlinPackages.io.github.solcott.countries.model.CountryDetail
typealias DataError = ExportedKotlinPackages.io.github.solcott.countries.model.DataError

typealias LoadStatus = ExportedKotlinPackages.io.github.solcott.countries.presenter.LoadStatus

/// The sealed-hierarchy members, which need naming a second time.
///
/// Swift export does generate the nested convenience names — `DataError.Network` and friends — but
/// emits them as typealiases with no access modifier, so they are `internal` to the generated module
/// and unreachable from here. Only the mangled top-level class is `public`.
///
/// The result is that flattening a sealed hierarchy by hand produces exactly the names Obj-C interop
/// used to mangle for free: `DataErrorNetwork`, `LoadStatusFailed`. Only construction needs them —
/// reading goes through `sealedType()`, which is typed and exhaustive and needs none of this.
typealias DataErrorNetwork =
  CountriesModel._ExportedKotlinPackages_io_github_solcott_countries_model_DataError_Network
typealias DataErrorHttp =
  CountriesModel._ExportedKotlinPackages_io_github_solcott_countries_model_DataError_Http
typealias DataErrorApi =
  CountriesModel._ExportedKotlinPackages_io_github_solcott_countries_model_DataError_Api
typealias DataErrorSerialization =
  CountriesModel._ExportedKotlinPackages_io_github_solcott_countries_model_DataError_Serialization
typealias DataErrorUnknown =
  CountriesModel._ExportedKotlinPackages_io_github_solcott_countries_model_DataError_Unknown

typealias LoadStatusLoading =
  CountriesPresenter._ExportedKotlinPackages_io_github_solcott_countries_presenter_LoadStatus_Loading
typealias LoadStatusIdle =
  CountriesPresenter._ExportedKotlinPackages_io_github_solcott_countries_presenter_LoadStatus_Idle
typealias LoadStatusFailed =
  CountriesPresenter._ExportedKotlinPackages_io_github_solcott_countries_presenter_LoadStatus_Failed
