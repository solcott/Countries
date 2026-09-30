---
paths:
  - "apple/**"
  - "iosApp/**"
  - "model/**"
---

# Swift export — what fails with no warning

`:apple` exports `:model` and the `dataresult`/`uistate` libraries to Swift **in full**. These rules
apply to anything reachable from them, which is why this file also loads for `model/`. Nothing in
the Kotlin build warns about any of it; the first signal is the iOS build, or a crash.

- **No Compose type in the exported API.** Compose's `Saver.save` (an extension-receiver method)
  generates a malformed thunk, so any Compose type is fatal. That is why `AppleUiState.kt` exists:
  Swift sees `CountryListUiState`/`CountryDetailUiState`, never `CountryListScreen.State` (it holds
  a `TextFieldState`). Keep `eventSink` `internal` — exporting `Event` risks dragging its sibling
  `State` along.
- **Sealed types that cross to Swift are `sealed class`, not `sealed interface`** (re-verified on
  Kotlin 2.4.20-RC):

  | | sealed interface | sealed class |
  | --- | --- | --- |
  | Read via `sealedType()` | works | works |
  | **Generic** (`Outcome<out T>`) | **generated Swift does not compile** | works |
  | Name or construct a member from another module | **unreachable** (typealias defaults to `internal`) | works |

  Screen `Event`s stay interfaces — they never cross to Swift.
- **`PresenterHolder` is deliberately not generic** — a type parameter would be erased to its bound.
  `state` is declared concretely on each subclass. Holders call `launchMolecule` directly, **not**
  wrapped in `presenterOf { }` (a `@ComposableTarget` mismatch), and expose `cancel()`.
- **A Kotlin class must not share the module's name.** `CountriesKit` is silently renamed
  `CountriesKit_`; the entry point is `CountriesCore`.
- **The deployment floor is iOS 18** — generated coroutine support uses `Synchronization.Mutex`.
- **Kotlin is pinned to 2.4.20 for Swift export** (`KotlinTypedStateFlow<T>`, `sealedType()`).
- **`export(...)` exports that module's API in full** and is the only way to set `flattenPackage`.
  Export only modules free of the above.
- `SwiftNavigator`'s Swift API is **country codes, not `Screen`s** — keep Circuit out of Swift.

## Linking SQLite

- **`-lsqlite3` comes from Xcode's `OTHER_LDFLAGS`** — Swift export produces a static library, which
  records no linker options.
- **`iosApp/Countries/Support/SQLiteLoadExtension.c` must stay.** It defines
  `sqlite3_enable_load_extension`/`sqlite3_load_extension`, which no Apple SDK exports but SQLiter's
  cinterop references. **Do not replace it with `-Wl,-U`** — that defers to a dyld crash before
  `main()` on macOS (iOS survives only via dead-code stripping). **It cannot move into Kotlin**:
  `@CName` is silently not emitted through Swift export, and a cinterop `.def` body is not compiled.
  Check the macOS debug dylib for `<flat-namespace>` imports:
  `dyld_info -imports …/Countries.app/Contents/MacOS/Countries.debug.dylib | grep flat-namespace`.

## Commands

```
# Apple bridge — the Kotlin half of the SwiftUI app
./gradlew :apple:macosArm64Test :apple:iosSimulatorArm64Test

# Inspect the generated Swift without going through Xcode. Swift export registers its tasks only
# when Xcode's environment variables are present, hence the prefix. Output lands in
# apple/build/SwiftExport/<target>/Debug/files/.
CONFIGURATION=Debug SDK_NAME=macosx ARCHS=arm64 TARGET_BUILD_DIR=/tmp/se \
FRAMEWORKS_FOLDER_PATH=Frameworks ./gradlew :apple:macosArm64DebugSwiftExport

# iOS / iPadOS / macOS app. Xcode runs the Gradle export itself, so open the project and hit run
# rather than building anything first.
open iosApp/Countries.xcodeproj
xcodebuild -project iosApp/Countries.xcodeproj -scheme Countries \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
xcodebuild -project iosApp/Countries.xcodeproj -scheme Countries \
  -destination 'platform=macOS,arch=arm64' build

# Unit tests and UI tests together. Run both destinations — several UI tests are device-shape
# specific and skip themselves on the shape they do not describe.
xcodebuild test -project iosApp/Countries.xcodeproj -scheme Countries \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
xcodebuild test -project iosApp/Countries.xcodeproj -scheme Countries \
  -destination 'platform=iOS Simulator,name=iPad mini (A17 Pro),OS=18.4'
```

**Tests run on iOS simulators only; the macOS destination is build-and-run.** `xcodebuild test` for
macOS fails with "Signing for CountriesUITests requires a development team" — Xcode builds every
testable in the scheme regardless of the target's `SUPPORTED_PLATFORMS` or of `-only-testing`, and a
macOS UI-test runner cannot be ad-hoc signed. Nothing is lost: the unit tests are pure functions
with no platform-specific behaviour, and they run on the simulator.

## Verify

- `./gradlew :apple:macosArm64Test :apple:iosSimulatorArm64Test`, then the iOS build — this is the
  only check between a `model` or `dataresult` change and a broken iOS build.
- `iosApp/`: `xcodebuild test` on **both** the iPhone and iPad simulator destinations; the macOS
  destination is build-and-run only.
- Switching branches that build the framework differently: wipe
  `~/Library/Developer/Xcode/DerivedData/Countries-*` first.

The reasons behind all of this, the Swift export bug write-ups and the test suites: the `apple-app`
skill.
