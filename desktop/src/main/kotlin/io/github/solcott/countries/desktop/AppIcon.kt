package io.github.solcott.countries.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

private const val ICON_PATH = "/icon.png"

/**
 * The window and dock icon, loaded from `desktop/icons/icon.png` on the classpath — the same file
 * jpackage bundles for Linux, and the source the `.icns` and `.ico` were derived from.
 *
 * Decoded once, eagerly: it is a 40 KB PNG read before the first frame, and `Window` wants a
 * [Painter] rather than a composable.
 */
internal val appIcon: Painter by lazy {
  val bytes =
    checkNotNull(AppIconMarker::class.java.getResourceAsStream(ICON_PATH)) {
        "Missing $ICON_PATH on the classpath"
      }
      .use { it.readBytes() }
  BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}

/** Anchors the classloader lookup above. */
private object AppIconMarker
