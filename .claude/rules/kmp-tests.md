---
paths:
  - "**/src/*Test/**"
  - "**/src/test/**"
---

# Tests in KMP modules

`src/commonTest` runs on **six** runners via `allTests`: `jvmTest`, `testAndroidHostTest`,
`jsBrowserTest`, `wasmJsBrowserTest`, `macosArm64Test`, `iosSimulatorArm64Test`. `kotlin("test")`
is wired in by `kmp-library`. Add `libs.kotlinx.coroutines.test` per module for `runTest`.

- **camelCase test names**, not backticked names with spaces. Only camelCase works on the JS and
  native runners.
- **No JUnit** in a KMP module. Use `kotlin.test` assertions and Turbine.
- **`SnapshotStateList.equals` is identity-based on native and JS** and structural on JVM, so call
  `.toList()` before `assertEquals`. Expect other JVM-only accidents like this.
- **`testAndroidHostTest` links the android.jar stubs.** A `SavedState`/`Bundle` saves nothing
  there and restores as `null`, failing on one runner out of six. Put such tests in `jvmTest`.
- **The web targets are `browser()` only. Never add `nodejs()`.** Molecule's frame clock never
  advances under Node. The browser runners need Chrome, and the Apple runners need Xcode and boot a
  simulator.
- **The first test in a Compose module needs `js { binaries.executable() }`, the same for `wasmJs`,
  and the Compose Multiplatform plugin.** Without them the task reports *"did not discover any
  tests"* instead of naming the cause. A karma `browserNoActivityTimeout` that is too short on CI
  produces the same message; see `shared-compose/karma.config.d/`.
- **The first native test binary that links the whole graph needs `linkerOpts("-lsqlite3")`.**
- Adding tests can change `kotlin-js-store/yarn.lock`. Regenerate it with `kotlinUpgradeYarnLock`,
  and also with `kotlinWasmUpgradeYarnLock` if the wasm lockfile moved too.
- An injected Kermit `Logger` can be asserted on with `kermit-test`'s `TestLogWriter`, which needs
  `@OptIn(ExperimentalKermitApi::class)`. `MappersTest` is the example.

Tests exist in `apple`, `repository`, `presenter`, `shared-compose`, `web` and `desktop`. A green
test task elsewhere ran nothing.
