import CountriesKit
import CountriesModel
import CountriesUiState
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
        Label("Couldn't Load Country", systemImage: "exclamationmark.triangle")
      } description: {
        Text(userMessage(for: failure))
      } actions: {
        Button("Retry") { model.holder.retry() }
          .buttonStyle(.borderedProminent)
      }
    } else if country == nil, phase.isSettled {
      ContentUnavailableView(
        "Country Not Found",
        systemImage: "questionmark.circle",
        description: Text("No country exists with the code \(code).")
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
        LabeledContent("Capital", value: country.capital ?? absentValue)
        LabeledContent("Continent", value: country.continentName)
        LabeledContent("Currency", value: country.currency ?? absentValue)
        LabeledContent("Calling code", value: "+\(country.phone)")
        LabeledContent("Languages", value: country.languageNames)
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
}
