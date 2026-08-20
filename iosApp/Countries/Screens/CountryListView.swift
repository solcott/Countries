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

  /// The chip row's height, which has to be explicit — see ``filterChips``. `@ScaledMetric` so it
  /// grows with Dynamic Type rather than clipping the capsules at the larger sizes.
  @ScaledMetric private var filterRowHeight: CGFloat = 48

  private var phase: LoadPhase { loadPhase(of: model.state.countriesStatus) }

  private var countries: [Country] { model.state.countries }

  private var continents: [Continent] {
    model.state.continents
  }

  private var selectedContinentCodes: Set<String> {
    Set(model.state.selectedContinents.map(\.code))
  }

  /// Everything currently narrowing the list, in the order the chip row shows it.
  ///
  /// Continents are the only filter today. When a second dimension arrives this becomes the place
  /// that concatenates them, and neither the chip row nor the count below has to learn about it.
  private var activeFilters: [Continent] { model.state.selectedContinents }

  private var countCaption: String { countriesCaption(countries.count) }

  /// The macOS window subtitle. Names the active filters before the count, because "61 countries"
  /// on its own does not say *which* 61.
  private var subtitleCaption: String {
    let names = activeFilters.map(\.name)
    guard !names.isEmpty else { return countCaption }
    return String(
      localized: .countryListFilterSubtitle(
        names.formatted(.list(type: .and, width: .narrow)),
        countCaption
      )
    )
  }

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
        .navigationSubtitle(subtitleCaption)
      #endif
      .task { await model.observe() }
  }

  /// The chip row sits above *every* state, not just the loaded list.
  ///
  /// The state you most need to clear a filter from is the empty one — a continent and a search
  /// term that between them match nothing. Putting the chips inside the `list` branch would hide
  /// them exactly then, leaving the toolbar menu as the only way out.
  ///
  /// `safeAreaInset` rather than a `VStack`, so the list scrolls *under* the row instead of being
  /// shortened by it — the row is chrome, not the first item of the content.
  private var content: some View {
    listContent
      .safeAreaInset(edge: .top, spacing: 0) {
        if !activeFilters.isEmpty {
          filterChips
        }
      }
  }

  /// The active filters, each removable by tapping it.
  ///
  /// A horizontal `ScrollView` rather than a wrapping layout: the row is fixed chrome above the
  /// list, so growing taller would push the list down. Scrolling sideways keeps it one row high
  /// however many filters are on. `fixedSize` is what holds it to one row — a horizontal
  /// `ScrollView` takes all the height it is offered otherwise, and here that was the whole top of
  /// the screen.
  ///
  /// The chip is drawn rather than styled with `.bordered`. That button style rendered *nothing*
  /// here — correctly positioned, hittable, and not a pixel painted — so the capsule and its
  /// colours are explicit and the button style is `.plain`.
  /// The active filters, each removable by tapping it.
  ///
  /// Horizontal scrolling rather than wrapping: the row is fixed chrome above the list, so growing
  /// taller would push the list down. It stays one row high however many filters are on, which is
  /// what makes room for a second filter dimension later.
  ///
  /// **`frame(height:)` is load-bearing.** Without an explicit height this `ScrollView` expanded to
  /// fill the space offered it — 216pt of it — and then painted nothing at all. The chips kept
  /// correct frames and stayed hittable the whole time, so `testActiveFilterChipShowsTheFilterAndClearsIt`
  /// passed against a screen showing an empty band. `fixedSize(horizontal:vertical:)` did not fix
  /// it; a concrete height did.
  private var filterChips: some View {
    ScrollView(.horizontal) {
      HStack(spacing: 8) {
        ForEach(activeFilters, id: \.code) { continent in
          Button {
            model.holder.toggleContinent(continent: continent)
          } label: {
            HStack(spacing: 5) {
              Text(continent.name)
                .font(.subheadline)
              Image(systemName: "xmark")
                .font(.caption2.weight(.bold))
            }
            .foregroundStyle(Color.accentColor)
            .padding(.leading, 12)
            .padding(.trailing, 10)
            .padding(.vertical, 6)
            .background(Color.accentColor.opacity(0.15), in: Capsule())
          }
          .buttonStyle(.plain)
          // The visible label is just the continent; VoiceOver needs to hear what tapping does.
          .accessibilityLabel(Text(.countryListRemoveFilter(continent.name)))
          .accessibilityIdentifier("filter-chip-\(continent.code)")
        }
        Spacer(minLength: 0)
      }
      .padding(.horizontal)
      .padding(.vertical, 8)
    }
    .frame(height: filterRowHeight)
    .scrollIndicators(.hidden)
    .accessibilityIdentifier("active-filters")
  }

  @ViewBuilder
  private var listContent: some View {
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
      // The count is on the control as well as in the chips: the chip row scrolls, so with several
      // filters on a phone the ones off the right edge would otherwise be invisible.
      Label(
        activeFilters.isEmpty
          ? String(localized: .countryListFilterLabel)
          : String(localized: .countryListFilterLabelCount(activeFilters.count)),
        systemImage: activeFilters.isEmpty
          ? "line.3.horizontal.decrease.circle"
          : "line.3.horizontal.decrease.circle.fill"
      )
    }
    .accessibilityIdentifier("continent-filter")
  }
}
