package org.umamo.format.psd

import org.umamo.format.art.LayerBounds
import org.umamo.format.art.LayerRaster
import kotlin.math.max
import kotlin.math.min

/**
 * Preprocessing that mirrors Anime2.5DRig's `cleanPsdLayers` + `cleanAlpha` (rigger.js): denoise a
 * decoded PSD layer's alpha and trim it to its content bounding box.
 *
 * Some PSDs carry low-alpha residue across the whole canvas (alpha 1..16 from feathered erasing or
 * export flattening).  Without denoising, that residue reads as opaque to the downstream classifier
 * and component splitter, so a part's content bbox stretches to the canvas edge and layers end up
 * displaced (eyes/eyebrows/iris/nose landing below the body).  This step drops stray sub-40px
 * components, dilates the survivors, and then crops the raster to the surviving content bbox so the
 * neutral layer keeps the tight bounds the rest of the pipeline expects.
 *
 * @see <a href="https://github.com/anime2.5d/rigger">rigger.js cleanPsdLayers / cleanAlpha</a>
 */
internal object PsdLayerCleanup {
	/** Alpha above this value counts as content when labelling connected components. */
	private const val NOISE_ALPHA_THRESHOLD = 16

	/** Connected components with fewer opaque pixels than this are treated as noise and dropped. */
	private const val MIN_COMPONENT_PIXELS = 40

	/** Dilation radius applied to the kept mask before zeroing everything outside it. */
	private const val DILATE_RADIUS = 3

	/** Padding kept around the content bbox when cropping. */
	private const val TRIM_PADDING = 4

	/**
	 * Denoises [raster]'s alpha and trims it to its content bbox, returning the new raster and the
	 * new canvas [LayerBounds] (left/top shifted to the crop origin).  A layer whose only content is
	 * noise comes back as a fully transparent raster of the original size, mirroring the reference.
	 *
	 * @param LayerRaster raster The decoded layer pixels (sized to the PSD layer rectangle).
	 * @param LayerBounds bounds The layer's original canvas placement (PSD layer rectangle).
	 * @return Pair The cleaned [LayerRaster] and its (possibly shifted/shrunk) [LayerBounds].
	 */
	fun clean(raster: LayerRaster, bounds: LayerBounds): Pair<LayerRaster, LayerBounds> {
		val width = raster.width
		val height = raster.height
		if (width <= 0 || height <= 0) return raster to bounds

		val pixelCount = width * height
		val alpha = ByteArray(pixelCount)
		for (index in 0 until pixelCount) alpha[index] = raster.rgba[index * 4 + 3]

		denoise(alpha, width, height)

		// Content bbox of the denoised alpha (threshold 0: survivors all have alpha > threshold).
		var x0 = width
		var y0 = height
		var x1 = -1
		var y1 = -1
		for (y in 0 until height) {
			for (x in 0 until width) {
				if ((alpha[y * width + x].toInt() and 0xff) == 0) continue
				if (x < x0) x0 = x
				if (x > x1) x1 = x
				if (y < y0) y0 = y
				if (y > y1) y1 = y
			}
		}

		if (x1 < 0) {
			// Noise-only layer: return the denoised (all-transparent) raster at the original bounds.
			val emptied = raster.rgba.copyOf()
			for (index in 0 until pixelCount) emptied[index * 4 + 3] = 0
			return LayerRaster(width, height, emptied) to bounds
		}

		val cropLeft = max(0, x0 - TRIM_PADDING)
		val cropTop = max(0, y0 - TRIM_PADDING)
		val cropRight = min(width - 1, x1 + TRIM_PADDING)
		val cropBottom = min(height - 1, y1 + TRIM_PADDING)
		val cropWidth = cropRight - cropLeft + 1
		val cropHeight = cropBottom - cropTop + 1

		if (cropWidth >= width && cropHeight >= height) {
			// Nothing to crop; still apply the denoised alpha.
			val cleaned = raster.rgba.copyOf()
			for (index in 0 until pixelCount) cleaned[index * 4 + 3] = alpha[index]
			return LayerRaster(width, height, cleaned) to bounds
		}

		val out = ByteArray(cropWidth * cropHeight * 4)
		for (y in 0 until cropHeight) {
			val srcRow = (y + cropTop) * width
			for (x in 0 until cropWidth) {
				val src = (srcRow + (x + cropLeft)) * 4
				val dst = (y * cropWidth + x) * 4
				out[dst] = raster.rgba[src]
				out[dst + 1] = raster.rgba[src + 1]
				out[dst + 2] = raster.rgba[src + 2]
				out[dst + 3] = alpha[srcRow + (x + cropLeft)]
			}
		}
		val newBounds = LayerBounds(bounds.left + cropLeft, bounds.top + cropTop, cropWidth, cropHeight)
		return LayerRaster(cropWidth, cropHeight, out) to newBounds
	}

	/**
	 * In-place denoise of [alpha]: label connected components above [NOISE_ALPHA_THRESHOLD], keep
	 * only those with at least [MIN_COMPONENT_PIXELS] pixels, dilate the kept mask by [DILATE_RADIUS],
	 * and zero every alpha byte outside the dilated mask.
	 */
	private fun denoise(alpha: ByteArray, width: Int, height: Int) {
		val pixelCount = width * height
		val labels = IntArray(pixelCount) // 0 = not content, >0 = component id
		val stack = IntArray(pixelCount)
		val sizes = ArrayList<Int>()
		sizes.add(0) // sizes[0] unused, mirrors the reference's 1-based component indexing
		var componentCount = 0

		for (seed in 0 until pixelCount) {
			if (labels[seed] != 0 || (alpha[seed].toInt() and 0xff) <= NOISE_ALPHA_THRESHOLD) continue
			componentCount++
			var top = 0
			stack[top++] = seed
			labels[seed] = componentCount
			var size = 0
			while (top > 0) {
				val q = stack[--top]
				size++
				val x = q % width
				val y = q / width
				if (x > 0) {
					val ni = q - 1
					if (labels[ni] == 0 && (alpha[ni].toInt() and 0xff) > NOISE_ALPHA_THRESHOLD) {
						labels[ni] = componentCount
						stack[top++] = ni
					}
				}
				if (x < width - 1) {
					val ni = q + 1
					if (labels[ni] == 0 && (alpha[ni].toInt() and 0xff) > NOISE_ALPHA_THRESHOLD) {
						labels[ni] = componentCount
						stack[top++] = ni
					}
				}
				if (y > 0) {
					val ni = q - width
					if (labels[ni] == 0 && (alpha[ni].toInt() and 0xff) > NOISE_ALPHA_THRESHOLD) {
						labels[ni] = componentCount
						stack[top++] = ni
					}
				}
				if (y < height - 1) {
					val ni = q + width
					if (labels[ni] == 0 && (alpha[ni].toInt() and 0xff) > NOISE_ALPHA_THRESHOLD) {
						labels[ni] = componentCount
						stack[top++] = ni
					}
				}
			}
			sizes.add(size)
		}

		if (componentCount == 0) return // no content above the threshold; leave alpha untouched

		val keep = BooleanArray(componentCount + 1)
		var anyKept = false
		for (component in 1..componentCount) {
			if (sizes[component] >= MIN_COMPONENT_PIXELS) {
				keep[component] = true
				anyKept = true
			}
		}
		if (!anyKept) {
			// Everything is noise: zero the whole alpha plane.
			for (index in 0 until pixelCount) alpha[index] = 0
			return
		}

		val mask = ByteArray(pixelCount)
		for (index in 0 until pixelCount) {
			if (labels[index] != 0 && keep[labels[index]]) mask[index] = 1
		}
		dilate(mask, width, height, DILATE_RADIUS)
		for (index in 0 until pixelCount) {
			if (mask[index].toInt() == 0) alpha[index] = 0
		}
	}

	/** Separable (horizontal then vertical) dilation of [mask] by [radius], matching rigger.js. */
	private fun dilate(mask: ByteArray, width: Int, height: Int, radius: Int) {
		val tmp = ByteArray(width * height)
		for (y in 0 until height) {
			val row = y * width
			for (x in 0 until width) {
				var value = 0
				val lo = max(0, x - radius)
				val hi = min(width - 1, x + radius)
				for (xx in lo..hi) {
					if (mask[row + xx].toInt() != 0) {
						value = 1
						break
					}
				}
				tmp[row + x] = value.toByte()
			}
		}
		for (x in 0 until width) {
			for (y in 0 until height) {
				var value = 0
				val lo = max(0, y - radius)
				val hi = min(height - 1, y + radius)
				for (yy in lo..hi) {
					if (tmp[yy * width + x].toInt() != 0) {
						value = 1
						break
					}
				}
				mask[y * width + x] = value.toByte()
			}
		}
	}
}
