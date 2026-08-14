---
name: compose-fonts
description: Why flags and non-Latin country names render as tofu or as letter pairs on some platforms, and what each platform needs. Use when flags or native names look wrong, when changing the bundled NotoColorEmoji flag font in desktop/, when touching LocalFlagFontFamily in :ui, or before changing the composeMultiplatform version — dropping below 1.12 silently reintroduces the web tofu bug.
---

# Fonts, flags and non-Latin text

One root cause on two platforms: **Skia has no system font manager**, so a codepoint with no glyph
in a font Skia has been *given* renders as tofu. Android and iOS are fine because those platforms
expose a system font fallback.

## Fonts on desktop

The outcome differs per platform, and the 1.12 web font downloader described below does not apply
here.

| Platform | Flags | Non-Latin native names |
| --- | --- | --- |
| macOS | Apple Color Emoji, fine | fine |
| Windows | **Segoe UI Emoji has no flag glyphs** — renders as the letter pair, e.g. "FR" | fine |
| Linux | tofu without Noto Color Emoji | tofu without Noto CJK etc. |

Windows omitting flag glyphs is Microsoft's deliberate policy, not a gap that will close. So
`:desktop` bundles `NotoColorEmoji-flagsonly.ttf` and provides it through `:ui`'s
`LocalFlagFontFamily`. That covers the flags. **It does not cover the non-Latin names on Linux** —
that would mean committing several MB of Noto CJK, and a Linux desktop that renders no CJK at all
is a system that will fail on far more than this app.

The font is upstream, verbatim, so updating it is a download:

```
curl -LO https://github.com/googlefonts/noto-emoji/raw/main/fonts/NotoColorEmoji-flagsonly.ttf
```

It is SIL Open Font License 1.1; the notice is committed beside it as
`desktop/src/main/resources/font/OFL.txt`.

Two rules that `FlagFontTest` pins, both discovered the hard way:

- **It must be the CBDT build, not `Noto-COLRv1.ttf`.** COLRv1 needs FreeType 2.11+ on Linux or a
  Windows 11-era DirectWrite to rasterise, and where it is unsupported it draws *nothing* rather
  than falling back. Blank is a worse failure than letters. CBDT stores each glyph as a PNG, which
  is the most widely supported colour format there is.
- **It must not be handed to macOS.** Skia goes through CoreText there, and CoreText refuses to
  load a bitmap-only font outright — `makeFromData` returns null, and the flags would disappear on
  the one platform that never needed the font. `needsBundledFlagFont()` is the guard, and
  `flagFontFamily` is null on macOS. (The COLRv1 build is not the escape hatch: CoreText loads it
  and then Skia's CoreText scaler renders its layers as nothing.)

The practical consequence for anyone changing this: **macOS cannot verify the font renders.** The
test states the invariant as "wherever Skia can load it, it must ligate and rasterise in colour;
where it cannot, the app must not be using it", which is the strongest thing a Mac can assert.
Flags on Windows and Linux need a real machine.


## Fonts on web

**Skia has no system font manager in a browser.** The browser's own fonts are unreachable from it —
Compose rasterises text into a canvas — so a codepoint with no glyph in a font Skia has been *given*
renders as tofu. Android, desktop and iOS are fine because those platforms expose a system font
fallback. This app hits it hard: flags on all 250 rows, and 53 countries whose `native` name needs
one of 14 non-Latin scripts.

Compose Multiplatform **1.12** solves it. `ComposeWindow` calls `installFallbackFontDownloader()`
unconditionally on web; Skia reports unresolved codepoints during layout, the downloader batches
them, fetches the matching Noto woff2 subsets from `https://fonts.gstatic.com/s/`, preloads them,
and calls `onNewFontInstalled()` to force a re-layout. Noto Color Emoji is in that table — chunk 0
is exactly the flag block `U+1f1e6-1f1ff` — and the CJK variant is picked from
`navigator.language`.

So:

- **Do not bundle fallback fonts, and do not hand-roll `FontFamily.Resolver.preload`.** That is the
  documented approach for 1.11 and earlier, and it is obsolete here. It would mean committing
  megabytes of woff2 and rebuilding the resolver on every late font arrival (`FontCache` is
  per-resolver, so a fresh one has to be re-preloaded with the whole accumulated set).
- **The downloader is unconditional — there is no property to turn it off.** The web app therefore
  makes runtime requests to a Google CDN, and non-Latin text and flags will not render offline
  until the browser has cached those files.
- **Do not drop `composeMultiplatform` below 1.12.** It reintroduces the bug silently: everything
  builds, and only a human looking at the running page notices.

