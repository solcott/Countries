import CountriesKit
import SwiftUI

/// The app's one navigation container.
///
/// `NavigationSplitView` rather than `NavigationStack` on purpose: it collapses to ordinary
/// push-and-pop on iPhone and expands to two real columns on iPad and Mac, so one construct gives
/// three correct behaviours. The selected country code *is* the navigation state — there is no
/// separate path array to keep in step with it.
struct RootView: View {

  let core: CountriesCore
  let commands: AppCommandBus

  @State private var navigation = NavigationModel()
  @State private var listModel: CountryListModel?

  /// Both columns, always — not `.automatic`.
  ///
  /// On **iPadOS 18 in portrait**, `.automatic` hides the sidebar outright: the app launches to a
  /// full-width "No Country Selected" with nothing but a toggle button in the corner, and the list
  /// cannot be seen at all. iPadOS 26 changed the default and shows both, which is exactly what
  /// makes this worth pinning rather than leaving to the system — the deployment floor is iOS 17,
  /// so the old behaviour is in scope. The list is the primary content here; it gets a real column.
  @State private var columnVisibility = NavigationSplitViewVisibility.all

  /// Which column the split view shows once it *collapses* to a stack — iPhone, and iPad in Slide
  /// Over or a narrow multitasking split. The default is `.detail`, which is the same empty screen
  /// by another route. SwiftUI moves this to `.detail` itself when a country is selected.
  @State private var compactColumn = NavigationSplitViewColumn.sidebar

  var body: some View {
    NavigationSplitView(
      columnVisibility: $columnVisibility,
      preferredCompactColumn: $compactColumn
    ) {
      Group {
        if let listModel {
          CountryListView(
            model: listModel,
            commands: commands,
            selection: $navigation.selectedCode
          )
        } else {
          ProgressView()
        }
      }
      .navigationSplitViewColumnWidth(min: 280, ideal: 340)
    } detail: {
      if let code = navigation.selectedCode {
        // Keyed by code so selecting a different country builds a fresh presenter rather than
        // reusing one bound to the previous country's flow.
        CountryDetailView(core: core, navigation: navigation, code: code)
          .id(code)
      } else {
        ContentUnavailableView(
          "No Country Selected",
          systemImage: "globe",
          description: Text("Choose a country from the list to see its details.")
        )
        .accessibilityIdentifier("no-country-selected")
      }
    }
    // .balanced gives the sidebar a proportional share of the window rather than letting the detail
    // take everything. It is what makes `columnVisibility = .all` read as two real columns on iPad
    // portrait. On iPhone the split view collapses to a stack and this has no effect.
    .navigationSplitViewStyle(.balanced)
    .task {
      // Built here rather than in an initialiser so the navigator exists first, and so exactly one
      // list presenter is created for the life of the view.
      if listModel == nil {
        listModel = CountryListModel(core.countryListPresenter(navigator: navigation.navigator))
      }
    }
    .onChange(of: navigation.selectedCode) {
      navigation.syncToKotlin()
    }
  }
}
