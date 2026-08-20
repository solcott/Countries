package io.github.solcott.countries.desktop

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the icon resource, which `build.gradle.kts` wires up in a way that is easy to break
 * without noticing: `icons/` is added as a resource root and `*.icns`/`*.ico` are excluded from the
 * jar. Widen that exclude, or move the directory, and the app loses its window and dock icon on
 * every desktop platform at once.
 *
 * The [applyTaskbarIcon] call itself is deliberately not exercised. Its whole body is two
 * `isSupported` environment queries, and running it would need a non-headless JVM and would put the
 * test runner in the dock.
 */
class AppIconTest {

  private val iconBytes =
    checkNotNull(javaClass.getResourceAsStream(ICON_RESOURCE)) {
        "$ICON_RESOURCE is not on the test classpath"
      }
      .use { it.readBytes() }

  @Test
  fun theIconDecodesAtTheSizeTheDockExpects() {
    val image =
      assertNotNull(ImageIO.read(ByteArrayInputStream(iconBytes)), "icon.png did not decode")
    // 512 is what the artwork ships at, and comfortably above the 256px a retina dock draws.
    assertEquals(512, image.width)
    assertEquals(512, image.height)
  }

  /** A blank or placeholder image would decode fine and look like a bug nobody wrote. */
  @Test
  fun theIconIsRealArtworkRatherThanABlankSquare() {
    val image = assertNotNull(ImageIO.read(ByteArrayInputStream(iconBytes)))
    val colours = buildSet {
      for (x in 0 until image.width) for (y in 0 until image.height) add(image.getRGB(x, y))
    }
    assertTrue(colours.size > 1, "expected real artwork, saw a single colour")
  }
}
