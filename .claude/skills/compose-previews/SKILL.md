---
name: compose-previews
description: How @Preview works in this project's commonMain Compose code — which artifact and import to use, the two project multipreview annotations, and the shared fixtures. Use when adding or changing a @Preview, or when previews render nothing in Android Studio. Every composable that emits UI is required to have one.
---

# Previews

Previews come from `org.jetbrains.compose.ui:ui-tooling-preview`, and the import is
**`androidx.compose.ui.tooling.preview.Preview`** — the AndroidX *names*, in `commonMain`. That
artifact is the Compose Multiplatform build of the AndroidX preview API: it publishes a variant for
every target this project has, and its common package is the `androidx` one, which is exactly why
Android Studio renders `commonMain` previews and why the AndroidX multipreviews
(`@PreviewScreenSizes`, `@PreviewLightDark`, `@PreviewFontScale`) and the full parameter list
(`widthDp`, `device`, `uiMode`, …) are available in common code. There is an older
`org.jetbrains.compose.ui.tooling.preview.Preview` from `components-ui-tooling-preview` — a bare
annotation with no parameters. Do not use it.

Two project multipreviews in `ui/…/PreviewSupport.kt`, so no preview repeats a device spec:

| Annotation | Use it on | Renders |
| --- | --- | --- |
| `@AppScreenPreviews` | whole screens | `@PreviewScreenSizes` (phone portrait/landscape, unfolded foldable, tablet portrait/landscape, desktop) plus a compact browser window |
| `@ComponentWidthPreviews` | a strip inside a screen | 360dp, 700dp, 1280dp |

The same file holds `PreviewSurface` (wraps in `AppTheme` + `Surface`), sample `Country` /
`Continent` / `CountryDetail` fixtures, and `loadedState` / `loadingState` / `refreshingState` /
`failedState` for building a `ContentState`. Use them rather than inventing new fixtures — the
sample list deliberately includes a country with a wrapping name and a null capital, which is what
breaks a row first.

Budget renders: the happy path gets the full size sweep, other states get `@PreviewLightDark` at
phone size. `AppTheme` reads `isSystemInDarkTheme()`, which the renderer drives from `uiMode`, so
`@PreviewLightDark` needs nothing passed to it.

`PreviewSurface` is the one composable that emits UI without a `@Preview` of its own — it *is* the
preview harness, so previewing it would be circular.

**`ui/build.gradle.kts` declares the renderer as `androidRuntimeClasspath`, not
`implementation`.** Android Studio resolves `ComposeViewAdapter` from the module's own runtime
classpath, so the annotations alone draw nothing; `androidRuntimeClasspath` is resolvable-only and
not a published variant, so the renderer is there for the IDE and never reaches `:app`. The AGP KMP
library plugin has no build types, so there is no `debugImplementation` to scope it with.

