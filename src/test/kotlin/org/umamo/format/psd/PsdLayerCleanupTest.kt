package org.umamo.format.psd

import org.umamo.format.art.LayerBounds
import org.umamo.format.art.LayerRaster
import kotlin.test.Test
import kotlin.test.assertEquals

class PsdLayerCleanupTest {

	private fun raster(width: Int, height: Int, alphaAt: (Int, Int) -> Int): LayerRaster {
		val rgba = ByteArray(width * height * 4)
		for (y in 0 until height) {
			for (x in 0 until width) {
				val i = (y * width + x) * 4
				rgba[i] = 0xff.toByte()
				rgba[i + 1] = 0xff.toByte()
				rgba[i + 2] = 0xff.toByte()
				rgba[i + 3] = alphaAt(x, y).toByte()
			}
		}
		return LayerRaster(width, height, rgba)
	}

	private fun alphaAt(raster: LayerRaster, x: Int, y: Int): Int =
		(raster.rgba[(y * raster.width + x) * 4 + 3].toInt() and 0xff)

	@Test
	fun trimsToContentBboxAndShiftsBounds() {
		val bounds = LayerBounds(left = 100, top = 50, width = 200, height = 200)
		// A single solid 40x40 blob at (50,60)..(89,99); no noise, so the trim is purely geometric.
		val source = raster(200, 200) { x, y -> if (x in 50..89 && y in 60..99) 255 else 0 }

		val (out, outBounds) = PsdLayerCleanup.clean(source, bounds)

		// Blob bbox 50..89 x 60..99 padded by 4 -> crop 46..93 x 56..103 (48x48).
		assertEquals(48, out.width)
		assertEquals(48, out.height)
		assertEquals(LayerBounds(146, 106, 48, 48), outBounds)
		// Blob survives at its crop-local position.
		assertEquals(255, alphaAt(out, 50 - 46, 60 - 56))
		assertEquals(255, alphaAt(out, 89 - 46, 99 - 56))
		assertEquals(0, alphaAt(out, 0, 0))
	}

	@Test
	fun removesLowAlphaNoiseAndSubComponentSpecks() {
		val bounds = LayerBounds(0, 0, 100, 100)
		// 20x20 blob at (40,40)..(59,59), a 3x3 sub-40px speck (alpha 200) near a corner, and
		// low-alpha (10) residue across the canvas edges. Without denoise the residue and speck
		// would stretch the content bbox to the full 100x100 canvas.
		val source = raster(100, 100) { x, y ->
			when {
				x in 40..59 && y in 40..59 -> 255
				x in 2..4 && y in 2..4 -> 200
				x == 0 || x == 99 || y == 0 || y == 99 || (x == 50 && (y == 0 || y == 99)) -> 10
				else -> 0
			}
		}

		val (out, outBounds) = PsdLayerCleanup.clean(source, bounds)

		// The residue and speck are dropped, so the bbox stays the blob only: 40..59 padded -> 36..63.
		assertEquals(28, out.width)
		assertEquals(28, out.height)
		assertEquals(LayerBounds(36, 36, 28, 28), outBounds)
		// Blob pixels are intact at their crop-local position; the crop corners stay transparent.
		assertEquals(255, alphaAt(out, 4, 4))
		assertEquals(255, alphaAt(out, 23, 23))
		assertEquals(0, alphaAt(out, 0, 0))
	}
}
