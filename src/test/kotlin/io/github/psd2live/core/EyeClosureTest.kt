package io.github.psd2live.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for the closed-eye pose.  The lid must fold down onto one straight line and
 * keep a band of body, the way the legacy rigger's `eyeOpen` fade does.  A per-column arch reads as
 * a thin U and a hairline collapse reads as a slit; neither is a blink.
 */
class EyeClosureTest {

	// Measured from the reference model: the left eye-white, and the lash band drawn above it.
	private val eyeWhite = Bounds(left = 652f, top = 273f, right = 716f, bottom = 316f)
	private val lashTop = 257f
	private val lashBottom = 329f

	/** The legacy close line: 62% down the eye-white box. */
	private val closeY = 273f + 43f * 0.62f

	private fun closedY(sourceY: Float) = RigBuilder.eyeClosurePoint(eyeWhite, sourceY)

	@Test
	fun closedLidKeepsABandOfBodyInsteadOfCollapsingToAHairline() {
		val closedHeight = closedY(lashBottom) - closedY(lashTop)

		// The legacy rigger retains 15% of the authored height, and never less.
		assertEquals((lashBottom - lashTop) * 0.15f, closedHeight, 0.05f)
		assertTrue(closedHeight > 6f, "closed lid is only ${closedHeight}px tall")
	}

	@Test
	fun closedLidStraddlesTheCloseLineInsteadOfSlidingOntoTheCheek() {
		val top = closedY(lashTop)
		val bottom = closedY(lashBottom)

		// Folding toward the line keeps both edges near it; translating would carry them off the eye.
		assertTrue(abs(top - closeY) < 8f, "closed lid top $top left the close line $closeY")
		assertTrue(abs(bottom - closeY) < 8f, "closed lid bottom $bottom left the close line $closeY")
	}

	@Test
	fun verticesOnTheCloseLineAreFixedPoints() {
		assertEquals(closeY, closedY(closeY), 0.01f)
	}
}
