package io.github.solcott.countries.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font

internal const val FLAG_FONT_RESOURCE = "/font/NotoColorEmoji-flagsonly.ttf"

/**
 * The font `:ui` should draw the flag emoji with, or `null` to leave them to the platform.
 *
 * Desktop is the one platform where the flags cannot simply be left to the OS, because Skia has no
 * system font manager of its own and draws with whatever font it is handed. On Windows that is
 * Segoe UI Emoji, which has no flag glyphs at all — Microsoft omits them deliberately — so every
 * row comes out as the two letters of the country code. A Linux install without Noto Color Emoji
 * renders tofu.
 *
 * macOS needs nothing: Apple Color Emoji has the flags and Skia's CoreText backend finds it. See
 * [needsBundledFlagFont] for why that is not merely an optimisation.
 */
internal val flagFontFamily: FontFamily? by lazy {
  if (!needsBundledFlagFont(System.getProperty("os.name").orEmpty())) return@lazy null
  val bytes =
    checkNotNull(FlagFontMarker::class.java.getResourceAsStream(FLAG_FONT_RESOURCE)) {
        "Missing $FLAG_FONT_RESOURCE on the classpath"
      }
      .use { it.readBytes() }
  FontFamily(Font(identity = "NotoColorEmojiFlags", data = bytes))
}

/**
 * Whether [osName] is a platform that needs the bundled font. Everything except macOS.
 *
 * Handing the bundled font to macOS would not be a harmless no-op, it would delete the flags. The
 * file is `NotoColorEmoji-flagsonly.ttf` as googlefonts/noto-emoji publishes it — a CBDT font,
 * meaning the glyphs are embedded PNGs and the outlines are empty — and Skia's macOS backend goes
 * through CoreText, which refuses to load a bitmap-only font at all: `makeFromData` returns null.
 *
 * The COLRv1 build is not the way out of that. CoreText loads it, and then Skia's CoreText scaler
 * renders the layers as nothing, which is worse — a blank column instead of a wrong one. CBDT is
 * also the safer of the two for the platforms that *do* need it: PNG glyph images are the most
 * widely supported colour format there is, where COLRv1 needs FreeType 2.11+ on Linux and a Windows
 * 11-era DirectWrite. Between "letters" and "blank", letters is the better failure.
 *
 * Pure and separate from the loading so it can be tested on a machine of either kind.
 */
internal fun needsBundledFlagFont(osName: String): Boolean = !osName.startsWith("mac", true)

/** Anchors the classloader lookup above. */
private object FlagFontMarker
