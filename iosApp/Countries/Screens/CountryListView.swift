import CountriesKit
import CountriesModel
import CountriesUiState
import SwiftUI

/// The country list.
///
/// Deliberately not a port of `CountryListUi.kt`. Same data, same states, same behaviour, but
/// expressed the way an Apple app expresses them: the system search bar rather than a text field
/// pinned inside the list, `ContentUnavailableView` rather than a centred column of text, and
/// pull-to-refresh rather than a progress strip above the rows.
struct CountryListView: View {

  let model: CountryListModel
  let commands: AppCommandBus

  @Binding var selection: String?

  @FocusState private var searchFocused: Bool

  /// SwiftUI owns the search text. The presenter keeps a Compose `TextFieldState`, which is
  /// snapshot-backed state with no meaning outside a composition, so the two are kept in step
  /// through `SearchTextChanged` instead of by sharing an object.
  @State private var query = ""

  private var phase: LoadPhase { loadPhase(of: model.state.countriesStatus) }

  private var countries: [Country] { model.state.countries }

  private var continents: [Continent] {
    model.state.continents
  }

  private var selectedContinentCodes: Set<String> {
    Set(model.state.selectedContinents.map(\.code))
  }

  private var countCaption: String { countriesCaption(countries.count) }

  var body: some View {
    content
      .navigationTitle(.countryListTitle)
      .searchable(text: $query, prompt: .countryListSearchPrompt)
      // macOS only, and not just because `.searchFocused` needs macOS 15: ⌘F is a menu-bar command,
      // and there is no menu bar on iOS. The iPhone and iPad search field is already on screen.
      #if os(macOS)
        .searchFocused($searchFocused)
        .onChange(of: commands.focusSearchTicks) { searchFocused = true }
      #endif
      .onChange(of: query) { _, newValue in
        model.holder.search(text: newValue)
      }
      .refreshable {
        model.holder.retry()
      }
      .toolbar {
        if !continents.isEmpty {
          ToolbarItem(placement: .primaryAction) { continentFilter }
        }
      }
      .onChange(of: commands.refreshTicks) {
        model.holder.retry()
      }
      #if os(macOS)
        // The Mac window has room for it, and it answers "did my filter do anything?" without
        // making the user count rows.
        .navigationSubtitle(countCaption)
      #endif
      .task { await model.observe() }
  }

  @ViewBuilder
  private var content: some View {
    if countries.isEmpty, phase.isLoading {
      ProgressView()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    } else if countries.isEmpty, let failure = phase.failure {
      ContentUnavailableView {
        Label(.countryListLoadFailedTitle, systemImage: "exclamationmark.triangle")
      } description: {
        Text(userMessage(for: failure))
      } actions: {
        Button(.commonRetry) { model.holder.retry() }
          .buttonStyle(.borderedProminent)
      }
    } else if countries.isEmpty, !query.isEmpty {
      ContentUnavailableView.search(text: query)
    } else if countries.isEmpty {
      ContentUnavailableView(
        .countryListEmptyTitle,
        systemImage: "globe",
        description: Text(.countryListEmptyDescription)
      )
    } else {
      list
    }
  }

  private var list: some View {
    List(countries, id: \.code, selection: $selection) { country in
      row(for: country)
    }
    #if os(macOS)
      .listStyle(.inset)
    #else
      .listStyle(.plain)
    #endif
  }

  private func row(for country: Country) -> some View {
    HStack(spacing: 12) {
      Text(country.emoji)
        .font(.title2)
      VStack(alignment: .leading, spacing: 2) {
        Text(country.name)
        if !country.subtitle.isEmpty {
          Text(country.subtitle)
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
      }
    }
    .padding(.vertical, 2)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(country.accessibilityDescription)
    // A stable handle for UI tests. The label above is prose for VoiceOver and would make a
    // brittle selector; the country code never changes.
    .accessibilityIdentifier("country-\(country.code)")
  }

  private var continentFilter: some View {
    Menu {
      ForEach(continents, id: \.code) { continent in
        Button {
          model.holder.toggleContinent(continent: continent)
        } label: {
          if selectedContinentCodes.contains(continent.code) {
            Label(continent.name, systemImage: "checkmark")
          } else {
            Text(continent.name)
          }
        }
      }
    } label: {
      Label(
        .countryListFilterLabel,
        systemImage: selectedContinentCodes.isEmpty
          ? "line.3.horizontal.decrease.circle"
          : "line.3.horizontal.decrease.circle.fill"
      )
    }
    .accessibilityIdentifier("continent-filter")
  }
}
