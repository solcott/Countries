import CountriesKit
import SwiftUI

/// The Apple entry point, and the counterpart to `MainActivity`, `:web`'s `main()` and `:desktop`'s
/// `main()`. Like all three it does as little as possible: build the object graph once, hand it to
/// the root view.
@main
struct CountriesApp: App {

  /// One graph for the lifetime of the process. `CoreGraph` is `@SingleIn(AppScope::class)`, so a
  /// second instance would mean a second Apollo client and a second normalized cache.
  private let core = CountriesCore()

  @State private var commands = AppCommandBus()

  var body: some Scene {
    WindowGroup {
      RootView(core: core, commands: commands)
        #if os(macOS)
          .frame(minWidth: 720, minHeight: 480)
        #endif
    }
    #if os(macOS)
      .defaultSize(width: 1000, height: 700)
      .commands { CountriesCommands(bus: commands) }
    #endif
  }
}
