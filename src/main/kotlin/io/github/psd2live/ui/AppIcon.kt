package io.github.psd2live.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Loads the app icon once, so the title-bar logo and the AWT window icon share a single
 * decode. The PNG is extracted from `icon.ico` and lives in `src/main/resources`.
 */
object AppIcon {
	val imageBitmap: ImageBitmap? by lazy {
		runCatching {
			val bytes = AppIcon::class.java.getResourceAsStream("/app_icon.png")?.use { it.readBytes() }
				?: return@runCatching null
			ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
		}.getOrNull()
	}

	val painter: Painter? by lazy { imageBitmap?.let { BitmapPainter(it) } }
}
