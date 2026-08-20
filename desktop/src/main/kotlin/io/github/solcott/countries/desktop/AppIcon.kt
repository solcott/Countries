package io.github.solcott.countries.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Taskbar
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.Image

internal const val ICON_RESOURCE = "/icon.png"

/**
 * The icon bytes, read from `desktop/icons/icon.png` on the classpath — the same file jpackage
 * bundles for Linux, and the source the `.icns` and `.ico` were derived from.
 *
 * Read once, eagerly: it is a 40 KB PNG read before the first frame, and the two consumers below
 * want it in two different shapes.
 */
private val iconBytes: ByteArray by lazy {
  checkNotNull(AppIconMarker::class.java.getResourceAsStream(ICON_RESOURCE)) {
      "Missing $ICON_RESOURCE on the classpath"
    }
    .use { it.readBytes() }
}

/**
 * The **window** icon, which is not the same thing as the dock icon — see [applyTaskbarIcon].
 *
 * `Window` wants a [Painter] rather than a composable, hence the eager decode.
 */
internal val appIcon: Painter by lazy {
  BitmapPainter(Image.makeFromEncoded(iconBytes).toComposeImageBitmap())
}

/** The same artwork as an AWT image, because [Taskbar] cannot take a [Painter]. */
internal val awtAppIcon: BufferedImage by lazy { ImageIO.read(ByteArrayInputStream(iconBytes)) }

/**
 * Tells macOS what to show in the Dock. Call before the first window exists.
 *
 * `Window(icon = appIcon)` is not enough, and looks like it should be. Compose Desktop's `icon`
 * parameter resolves to `java.awt.Window.setIconImage` — a *title bar* icon, which is what Windows
 * and Linux want and which macOS has no concept of. macOS takes the Dock icon from the app bundle
 * or from [Taskbar], and Compose Desktop never touches [Taskbar]. So a packaged build looked right
 * — jpackage writes `Countries.icns` and `CFBundleIconFile` into the bundle — while `:desktop:run`
 * and the uber jar showed the default Java coffee cup.
 *
 * Both guards are load-bearing rather than defensive noise. [Taskbar.getTaskbar] throws
 * `UnsupportedOperationException` where there is no taskbar at all, including headless, and
 * [Taskbar.Feature.ICON_IMAGE] is unsupported on Windows and most Linux desktops, where assigning
 * it throws. `isSupported` is how you ask rather than catch. This is therefore a no-op everywhere
 * but macOS, which is right: the other two get their icon from `Window`.
 */
internal fun applyTaskbarIcon() {
  if (!Taskbar.isTaskbarSupported()) return
  val taskbar = Taskbar.getTaskbar()
  if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
  taskbar.iconImage = awtAppIcon
}

/** Anchors the classloader lookup above. */
private object AppIconMarker
