package io.github.solcott.countries.desktop

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine

/**
 * Guards the bundled flag font, which nothing else would notice breaking: swap it for a monochrome
 * subset, or for the COLRv1 build, and all 250 rows quietly lose their flags on the two platforms
 * that cannot be checked from here.
 */
class FlagFontTest {

  private val fontBytes =
    checkNotNull(javaClass.getResourceAsStream(FLAG_FONT_RESOURCE)) {
        "$FLAG_FONT_RESOURCE is not on the test classpath"
      }
      .use { it.readBytes() }

  @Test
  fun macOsUsesItsOwnEmojiFont() {
    assertFalse(needsBundledFlagFont("Mac OS X"))
    assertFalse(needsBundledFlagFont("macOS"))
  }

  @Test
  fun windowsAndLinuxNeedTheBundledFont() {
    assertTrue(needsBundledFlagFont("Windows 11"))
    assertTrue(needsBundledFlagFont("Windows 10"))
    assertTrue(needsBundledFlagFont("Linux"))
  }

  /**
   * The colour glyphs must be CBDT bitmaps. COLRv1 needs FreeType 2.11+ or a Windows 11-era
   * DirectWrite to rasterise, and where it is unsupported it draws nothing at all rather than
   * falling back — see the note on [needsBundledFlagFont].
   */
  @Test
  fun theBundledFontIsTheBitmapColourBuild() {
    val tables = sfntTableTags(fontBytes)
    assertContains(tables, "CBDT")
    assertContains(tables, "CBLC")
    // ccmp lives here: a flag is a ligature of two regional indicators, and without GSUB the font
    // has every glyph and forms none of them.
    assertContains(tables, "GSUB")
    assertFalse("COLR" in tables, "expected the CBDT build, got the COLRv1 one")
  }

  /**
   * States the invariant across both kinds of machine: wherever Skia can load the font we require
   * it to ligate and rasterise in colour, and where it cannot load it we require that the app was
   * never going to use it. On macOS it is the second branch that runs — CoreText rejects a
   * bitmap-only font — and [flagFontFamily] is null there for exactly that reason.
   */
  @Test
  fun theFontRendersFlagsWhereverThisPlatformCanLoadIt() {
    val typeface = FontMgr.default.makeFromData(Data.makeFromBytes(fontBytes))
    if (typeface == null) {
      assertFalse(
        needsBundledFlagFont(System.getProperty("os.name").orEmpty()),
        "Skia cannot load the bundled font on a platform that depends on it",
      )
      return
    }

    assertEquals("Noto Color Emoji Flags", typeface.familyName)

    val line = TextLine.make(FRANCE, Font(typeface, FONT_SIZE))
    assertEquals(1, line.glyphs.size, "the regional indicator pair did not ligate")

    val surface = Surface.makeRasterN32Premul(CANVAS, CANVAS)
    surface.canvas.clear(WHITE)
    surface.canvas.drawTextLine(line, MARGIN, BASELINE, Paint())
    val png = checkNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)).bytes
    val image = ImageIO.read(ByteArrayInputStream(png))
    val colours = buildSet {
      for (x in 0 until image.width) for (y in 0 until image.height) add(image.getRGB(x, y))
    }
    // The French flag is three, plus the background; the margin allows for antialiasing.
    assertTrue(colours.size > 4, "expected a colour flag, saw ${colours.size} distinct colours")
  }

  /** The sfnt table directory: a 12-byte header, then one 16-byte record per table, tag first. */
  private fun sfntTableTags(bytes: ByteArray): Set<String> =
    DataInputStream(ByteArrayInputStream(bytes)).use { input ->
      input.skipBytes(4)
      val tableCount = input.readUnsignedShort()
      input.skipBytes(6)
      buildSet {
        repeat(tableCount) {
          val tag = ByteArray(4).also(input::readFully).decodeToString()
          input.skipBytes(12)
          add(tag)
        }
      }
    }

  private companion object {
    const val FRANCE = "🇫🇷"
    const val CANVAS = 96
    const val FONT_SIZE = 64f
    const val MARGIN = 8f
    const val BASELINE = 76f
    val WHITE = 0xFFFFFFFF.toInt()
  }
}
