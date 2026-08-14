import CountriesKit
import CountriesModel
import CountriesUiState
import Foundation
import SwiftUI

/// One country's details.
///
/// Same fields as `CountryDetailUi.kt`, laid out as a grouped `Form` of `LabeledContent` rows —
/// which is what a native detail screen looks like — rather than as the Compose column of
/// "Label: value" `Text`s.
struct CountryDetailView: View {

  let core: CountriesCore
  let navigation: NavigationModel
  let code: String

  @State private var model: CountryDetailModel?

  var body: some View {
    Group {
      if let model {
        content(for: model)
          .task { await model.observe() }
      } else {
        ProgressView()
      }
    }
    .task {
      if model == nil {
        model = CountryDetailModel(
          core.countryDetailPresenter(code: code, navigator: navigation.navigator)
        )
      }
    }
  }

  @ViewBuilder
  private func content(for model: CountryDetailModel) -> some View {
    let country = model.state.country
    let phase = loadPhase(of: model.state.status)

    if country == nil, phase.isLoading {
      ProgressView()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    } else if country == nil, let failure = phase.failure {
      ContentUnavailableView {
        Label(.countryDetailLoadFailedTitle, systemImage: "exclamationmark.triangle")
      } description: {
        Text(userMessage(for: failure))
      } actions: {
        Button(.commonRetry) { model.holder.retry() }
          .buttonStyle(.borderedProminent)
      }
    } else if country == nil, phase.isSettled {
      ContentUnavailableView(
        .countryDetailNotFoundTitle,
        systemImage: "questionmark.circle",
        description: Text(.countryDetailNotFoundDescription(code))
      )
    } else if let country {
      detail(for: country)
    }
  }

  private func detail(for country: CountryDetail) -> some View {
    Form {
      Section {
        VStack(spacing: 8) {
          Text(country.emoji)
            .font(.system(size: 64))
            .accessibilityHidden(true)
          Text(country.name)
            .font(.title2.bold())
          Text(country.nativeName)
            .font(.body)
            .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .listRowBackground(Color.clear)
      }

      Section {
        field(.countryDetailCapital, country.capital ?? absentValue)
        field(.countryDetailContinent, country.continentName)
        field(.countryDetailCurrency, country.currency ?? absentValue)
        // "+" then digits is E.164 notation, not language, so it stays a literal.
        field(.countryDetailCallingCode, "+\(country.phone)")
        field(.countryDetailLanguages, country.languageNames)
      }
    }
    .formStyle(.grouped)
    .accessibilityIdentifier("country-detail")
    .textSelection(.enabled)
    .navigationTitle(country.name)
    #if !os(macOS)
      .navigationBarTitleDisplayMode(.inline)
    #endif
  }

  /// One `LabeledContent` row, built from a catalog symbol.
  ///
  /// `LabeledContent(_:value:)` does have a `LocalizedStringResource` overload, but only from
  /// iOS 26 — above this app's iOS 18 floor. The content/label builder form is iOS 16 and renders
  /// identically, so the label still comes from the catalog rather than a literal.
  private func field(_ label: LocalizedStringResource, _ value: String) -> some View {
    LabeledContent {
      Text(value)
    } label: {
      Text(label)
    }
  }
}
