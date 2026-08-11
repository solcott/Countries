import SwiftUI

/// Menu-bar commands, and the channel that carries them to the view.
///
/// A `Commands` builder lives on the `Scene`, not in the view hierarchy, so a menu item cannot
/// reach `CountryListView` directly. Each command bumps a counter here and the view watches it with
/// `.onChange` — a plain, visible mechanism, and enough for two commands. (`FocusedValue` is the
/// heavier alternative, and it earns its keep when commands must act on whichever window is
/// frontmost. There is one window here.)
@Observable
@MainActor
final class AppCommandBus {
  private(set) var focusSearchTicks = 0
  private(set) var refreshTicks = 0

  func focusSearch() { focusSearchTicks += 1 }

  func refresh() { refreshTicks += 1 }
}

/// macOS only. iOS has no menu bar, and both actions are already reachable there — the search field
/// is on screen and refresh is pull-to-refresh.
struct CountriesCommands: Commands {

  let bus: AppCommandBus

  var body: some Commands {
    CommandGroup(after: .toolbar) {
      Button("Find") { bus.focusSearch() }
        .keyboardShortcut("f", modifiers: .command)

      Button("Refresh") { bus.refresh() }
        .keyboardShortcut("r", modifiers: .command)
    }
  }
}
