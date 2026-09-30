---
paths:
  - "ui/**/*.kt"
---

# `:ui` — Compose UI

- **`:ui` has no `android.*` imports at all.** There is no `AndroidView` escape hatch on five of six
  platforms.
- **Exactly one platform seam: `LocalFlagFontFamily`**, null everywhere but desktop. Do not add a
  second — see the `compose-fonts` skill.
- **How a platform looks is an `AppSkin` parameter, not a seam.** `MaterialSkin`, `DesktopSkin`,
  `WebSkin` live in `ui/…/theme/`; composables read tokens from `LocalAppSkin`. `contentMaxWidth`
  and `contentPanel` are what make `:web` read as a page. **`WebSkin`'s page colour is duplicated in
  `web/…/styles.css`** — change both.
- `AppSkin` detail: a skin has no `expect`/`actual`, lives in no platform source set, and can be
  rendered from Android Studio — which is why the UI is not forked per platform. `AppTheme` also
  feeds `minInteractiveSize` into `LocalMinimumInteractiveComponentSize`, the one value that takes
  the whole Material control set from a 48dp touch target to pointer density.
- Screen-agnostic wiring (theme, backstack, `CircuitCompositionLocals`, `NavigableCircuitContent`)
  belongs in `CountriesApp`, not in a screen or an entry point.
- The Ui is a pure function of Circuit state and emits events — no business logic, no data access.
- In a multi-pane layout Circuit stops collecting for the non-current record; the
  `ProvideRecordLifecycle(isActive = true)` in `ListDetailNavDecoration.kt` is what keeps the list
  pane live. Keep it.

## Modifiers

**Every composable that emits UI takes `modifier: Modifier = Modifier` as its first optional
parameter and applies it to its root element**, private helpers included. detekt's `ModifierMissing`
and `ModifierNotUsedAtRoot` enforce this in `./gradlew build`. The exceptions emit nothing:
`AppTheme` (a wrapper) and `DataError.toUserMessage()` (returns a `String`).

## Previews — required for every composable that emits UI

Nothing enforces this; the `compose-conventions` subagent audits it.

- **Import `androidx.compose.ui.tooling.preview.Preview`** (from
  `org.jetbrains.compose.ui:ui-tooling-preview` — the AndroidX names, in `commonMain`). **Not**
  `org.jetbrains.compose.ui.tooling.preview.Preview`, the older parameterless annotation.
- Project multipreviews in `PreviewSupport.kt`:

  | Annotation | Use on | Renders |
  | --- | --- | --- |
  | `@AppScreenPreviews` | whole screens | `@PreviewScreenSizes` plus a compact browser window |
  | `@ComponentWidthPreviews` | a strip inside a screen | 360dp, 700dp, 1280dp |

- **Budget:** the happy path gets the full size sweep; loading, error, empty and refreshing get
  `@PreviewLightDark` at phone size. Preview the states that break, not just the happy path.
- **Reuse the fixtures** in `PreviewSupport.kt` — `PreviewSurface`, `loadedState`, `loadingState`,
  `refreshingState`, `failedState`, and the sample `Country`/`Continent`/`CountryDetail`. The sample
  list deliberately includes a wrapping name and a null capital.
- `PreviewSurface` is the one exception: it is the harness.
- **The renderer is `androidRuntimeClasspath`, not `implementation`** in `ui/build.gradle.kts` —
  the IDE needs `ComposeViewAdapter` there, and this keeps it out of `:app`. If previews render
  nothing, check that first.

## Verify

`./gradlew :ui:assemble assembleDebug` — `:ui` has no tests. To see it, use the desktop app under
hot reload (the `desktop-app` skill).
