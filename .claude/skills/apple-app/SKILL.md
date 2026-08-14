---
name: apple-app
description: The hand-written SwiftUI app for iOS, iPadOS and macOS and the :apple Kotlin bridge behind it. Use when editing anything under iosApp/ or apple/, or when touching Kotlin Swift export, Molecule presenter holders, the AppleUiState facade, SwiftNavigator, the Xcode integration, or the Apple test suites. Covers the sealed-class-vs-interface export rules, the iOS 18 deployment floor, and why several of these fail with no warning.
---

# The Apple app

The SwiftUI app and its Kotlin bridge. See `AGENTS.md` for the module map and the project-wide
conventions this sits inside.

## The `apple` module

The Kotlin half of the SwiftUI app, exported to Swift and linked by
`iosApp/Countries.xcodeproj`. One target covers iPhone, iPad and Mac — hence `:apple`, not `:ios`.

**The UI is hand-written SwiftUI and is not a port of the Compose design.** Same data, same states,
same behaviour, expressed with Apple idioms: `.searchable` rather than a text field pinned in the
list, `ContentUnavailableView` rather than `ErrorContent`, pull-to-refresh rather than a progress
strip, `Form`/`LabeledContent` rather than a column of "Label: value" text.

SwiftUI rather than UIKit because **UIKit does not run on macOS** — that is AppKit, a different
framework — and the usual escape hatch is closed: **Kotlin/Native has no Mac Catalyst target**, so a
KMP framework cannot link into a Catalyst app.

Five things worth knowing:

- **Presenters reach Swift through Molecule, not directly.** A Circuit presenter here is a
  `@Composable` function with a hidden `$composer` parameter, so Swift cannot call it at all.
  `PresenterHolders.kt` runs it with `launchMolecule(RecompositionMode.Immediate)` and exposes a
  `StateFlow`. That is why `:apple` applies the Compose compiler plugin despite rendering nothing,
  and why the framework links Compose runtime and foundation. Unlike Circuit's counter sample the
  holders do **not** wrap in `presenterOf { }` — `launchMolecule` already takes a `@Composable`
  lambda, and wrapping introduces a `@ComposableTarget("presenter")` mismatch — and they expose
  `cancel()`, which the sample omits and a repeatedly-opened detail screen needs.
- **`PresenterHolder`, the shared base, is deliberately not generic.** The scope, `cancel()` and the
  Molecule launch are shared; `state` stays declared concretely on each subclass. A
  `PresenterHolder<UiState>` would have its type parameter erased to its upper bound. The Swift side
  *is* generic (`PresenterModel<Holder>`), which is fine: Swift generics are real.
- **Swift export is why `kotlin` is pinned to 2.4.20.** `StateFlow` arrives as
  `KotlinTypedStateFlow<T>` with a typed non-optional `value` and `asAsyncSequence()`; sealed types
  get a generated `sealedType()` returning an exhaustively switchable Swift enum. Both landed after
  2.4.10, so dropping below 2.4.20 costs the two things that make the exported API usable from
  Swift at all.
- **Sealed types that cross to Swift are `sealed class`, not `sealed interface`.** The release notes
  say 2.4.20-RC "adds support for sealed classes and interfaces", and that is true of the pattern
  their example shows — *reading* a non-generic hierarchy through `sealedType()`. Two things outside
  that pattern still do not work, both **re-verified against 2.4.20-RC**, and nothing warns you:

  | | sealed interface | sealed class |
  | --- | --- | --- |
  | Read via `sealedType()` | works | works |
  | **Generic** (`Outcome<out T>`) | **generated Swift does not compile** | works |
  | Name or construct a member (`DataError.Network`) from another module | **unreachable** | works |

  The generic case fails because the erased subtype cannot be made to conform to the erased parent
  protocol; as a class it is plain subclassing, which survives erasure. The member case fails
  because the nested convenience names are emitted as typealiases with **no access modifier**, so
  they default to `internal` in the generated module while only the mangled top-level class is
  `public`.

  So `Outcome` has no choice — it is generic. `DataError` and `LoadStatus` are a deliberate
  trade: the app only ever *reads* them through `sealedType()` and would be fine either way, but
  `CountriesTests` constructs their members, and as interfaces that would mean reintroducing a file
  of mangled-name aliases. Sealed classes cost the more idiomatic Kotlin and buy no shim.

  `Event` in the two Screens stays an interface — it has `CircuitUiEvent` as a second supertype, and
  it never crosses to Swift.
- **`export(project(…))` means something different here than on an Obj-C framework.** Swift export
  already emits everything reachable from the module's public API, so exporting is not what makes
  types visible — it is the only way to set `flattenPackage`, and it exports that module's API *in
  full*. Only `:dataresult`, `:model` and `:uistate` are exported, because only they are free of
  anything the generator rejects.
- **`-lsqlite3` moved to Xcode.** Swift export produces a static library, which records no linker
  options, so SQLiter's symbols resolve at the app link via `OTHER_LDFLAGS`. macOS additionally
  needs `-Wl,-U,_sqlite3_load_extension` and `-Wl,-U,_sqlite3_enable_load_extension`: Apple's system
  libsqlite3 omits both, SQLiter references and never calls them, and the old *dynamic* framework
  never had to resolve them.
- **The deployment floor is iOS 18, not 17.** Swift export's generated coroutine support uses
  `Synchronization.Mutex`. Nothing in the documentation mentions a minimum OS.
- **A Kotlin class must not share the module's name.** `CountriesKit` would be silently renamed to
  `CountriesKit_` in Swift; the entry point is `CountriesCore` for that reason.

Navigation is Swift's. `NavigationSplitView` with a `selection: String?` is the whole navigation
state — it collapses to push-and-pop on iPhone and gives two columns on iPad and Mac. `SwiftNavigator`
forwards navigation into Swift closures and mirrors the stack back through `syncFromSwift`, so there
is exactly one writer and none of the feedback-loop guarding `BrowserHistory` needs. Its Swift-facing
API is **country codes, not `Screen`s** — the two Circuit `Screen`s are built inside `SwiftNavigator`,
which keeps Circuit out of the Swift surface entirely.

**`AppleUiState.kt` is the boundary, and it exists because of `TextFieldState`.** Swift never sees
`CountryListScreen.State`: it carries a Compose `TextFieldState`, and Swift export generates
uncompilable Swift for Compose's `Saver` (its `save` takes an extension receiver, and the generated
reverse-interop thunk drops it). `:apple` publishes `CountryListUiState`/`CountryDetailUiState`
instead — no Compose, no Circuit, no generics — and replaces `eventSink` with named methods on the
holder. Keeping `eventSink` `internal` matters: `CountryListScreen.Event` is a sibling nested type of
`CountryListScreen.State`, so exporting one risks dragging the other, and `TextFieldState` with it.

Xcode integration is the standard KMP "direct integration": a Run Script phase runs
`:apple:embedSwiftExportForXcode`, which handles only the slice Xcode is currently targeting. There
is no framework and nothing to sign — Swift export emits Swift source, builds it as a synthetic SPM
package, and copies a static `libCountriesKit.a` plus the `.swiftmodule` interfaces into
`$BUILT_PRODUCTS_DIR`. `project.pbxproj` uses a `PBXFileSystemSynchronizedRootGroup`, so new Swift
files need no project edit.


## Swift export bugs worth reporting

Found while porting, all against Kotlin 2.4.20 (Beta2, and still present on RC), none of them
documented. A YouTrack search
turned up no existing report for either of the first two, though that search was shallow:

1. **A generic sealed interface generates Swift that does not compile.** `sealed interface
   Outcome<out T>` produces `cannot convert value of type '…Outcome_Data' to specified type
   '…Outcome'` — the erased subtype is not made to conform to the erased parent protocol. Workaround:
   make it a `sealed class`. **Still present on 2.4.20-RC**, the release that claims sealed-interface
   support; a non-generic sealed interface is fine, so the gap is specifically generics.
2. **Sealed interface members are unreachable across modules.** The nested convenience names
   (`DataError.Network`) are emitted as typealiases with no access modifier, so they default to
   `internal` in the generated module while only the mangled top-level class is `public`. Workaround:
   make it a `sealed class`. **Still present on 2.4.20-RC.** Reading through `sealedType()` is
   unaffected — this only bites code that names or constructs a member.
3. **`Saver.save` — a method with an extension receiver — generates a malformed reverse-interop
   thunk**, passing the receiver as the `value:` argument and omitting the receiver. This is what
   makes any Compose type in the exported API fatal.
4. **The generated coroutine support requires iOS 18** (`Synchronization.Mutex`) with no
   documentation of a minimum OS, and no diagnostic beyond a Swift compile error deep in generated
   code.


## Testing the Apple app

Three suites, and they are deliberately different tools:

| Where | Tool | Covers |
| --- | --- | --- |
| `apple/src/commonTest` | `kotlin.test` | `SwiftNavigator`, and that Molecule turns a `@Composable` presenter into an observable `StateFlow` |
| `iosApp/CountriesTests` | Swift Testing | the pure Swift presentation logic — `userMessage(for:)`, `loadPhase`, row subtitles |
| `iosApp/CountriesUITests` | **XCTest** | the touch paths: selection, search, continent filter, back, and the iPad two-column layout |

The UI tests are XCTest rather than Swift Testing because Swift Testing has no UI-testing support —
`XCUIApplication` assertions require `XCTestCase`. That inconsistency is forced, not an oversight.

Two things to know before adding a UI test:

- **Fixtures must be at the top of the list.** `List` only realises visible rows, so a country
  further down does not exist as an accessibility element until it is scrolled to. The first draft
  of these tests used Canada and every one failed on a row that was merely off screen; they use
  Andorra and the UAE, rows one and two, via the constants in `UITestSupport.swift`.
- **`LabeledContent` merges its label and value into one element**, so the detail screen's values
  are not addressable as exact `staticTexts`. Match with a `label CONTAINS` predicate.

`NavigationUITests` branches on `UIDevice.current.userInterfaceIdiom` with `XCTSkipUnless`, so the
suite is meaningful on both destinations rather than passing vacuously on one.
`testListAndDetailAreVisibleTogetherOnIPad` is the regression test for the iPad portrait bug and has
been confirmed to fail against the pre-fix `.automatic` column visibility.

These tests hit the live GraphQL API, so a cold simulator needs network; reruns are warm from the
Apollo SQLite cache. A hermetic version would need a launch argument swapping in a stub repository,
which means production code changing shape for tests — not done.

