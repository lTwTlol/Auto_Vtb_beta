package io.github.psd2live.core

import org.umamo.format.moc3.Moc3
import org.umamo.interop.moc3.import.Moc3Import
import org.umamo.render.eval.CpuDeformationEvaluator
import org.umamo.runtime.model.ParameterId
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.hypot

/**
 * Development tool: poses an exported .moc3 at key parameters and measures drawable displacement,
 * plus a neutral-visibility audit that catches the "invisible layers in VTube Studio" regression.
 */
object Moc3PoseTest {
	@JvmStatic
	fun main(args: Array<String>) {
		if (args.isNotEmpty() && args[0] == "--soak") {
			io.github.psd2live.core.PhysicsSoakTest().main(arrayOf(args.getOrNull(1) ?: ""))
			return
		}
		run(args[0])
	}

	fun run(moc3Path: String) {
		val puppet = Moc3Import.fromMocDocument(Moc3.read(Files.readAllBytes(Paths.get(moc3Path))), null)
		val evaluator = CpuDeformationEvaluator()

		// ---- visibility audit: any drawable flat at neutral is what VTS will not show ----
		val neutral = evaluator.evaluate(puppet, emptyMap())
		val hidden = mutableListOf<String>()
		for (drawable in puppet.drawables) {
			val opacity = neutral.opacity[drawable.id]
			if (opacity != null && opacity < 0.01f) hidden += drawable.id.raw
		}
		if (hidden.isEmpty()) println("[visibility] OK - no hidden drawables at neutral")
		else println("[visibility] HIDDEN (${hidden.size}): ${hidden.joinToString(", ")}")

		// ---- pose displacement ----
		val poses = linkedMapOf(
			"LegX2=+10" to mapOf(ParameterId("ParamLegX2") to 10f),
			"LegZ11=+10" to mapOf(ParameterId("ParamLegZ11") to 10f),
			"LegZ22=+10" to mapOf(ParameterId("ParamLegZ22") to 10f),
			"BustX=+1" to mapOf(ParameterId("ParamBustX") to 1f),
			"Bust=+1" to mapOf(ParameterId("ParamBust") to 1f),
			"BodyX=+10" to mapOf(ParameterId("ParamBodyAngleX") to 10f),
			"Skirt=+1" to mapOf(ParameterId("ParamSkirtSwing") to 1f),
			"HairBack=-1.0" to mapOf(ParameterId("ParamHairBack") to -1f),
			"HairBack=-1.05" to mapOf(ParameterId("ParamHairBack") to -1.05f),
			"HairBack=-1.094" to mapOf(ParameterId("ParamHairBack") to -1.094f),
			"HairBack=+1.0" to mapOf(ParameterId("ParamHairBack") to 1f),
			"HairBack=+1.094" to mapOf(ParameterId("ParamHairBack") to 1.094f),
		)
		for ((name, pose) in poses) {
			val geo = evaluator.evaluate(puppet, pose)
			val parts = mutableListOf<String>()
			for (drawable in puppet.drawables) {
				val n = drawable.id.raw
				val lower = n.lowercase()
				if (!(lower.contains("leg") || lower.contains("bust") || lower.contains("topwear") ||
					lower.contains("bottomwear") || lower.contains("skirt") || lower.contains("hair"))
				) continue
				val a = neutral.worldPositions[drawable.id] ?: continue
				val b = geo.worldPositions[drawable.id] ?: continue
				var maxD = 0.0
				for (i in a.indices step 2) {
					val dx = b[i] - a[i]
					val dy = b[i + 1] - a[i + 1]
					maxD = maxOf(maxD, hypot(dx.toDouble(), dy.toDouble()))
				}
				parts.add("$n=${"%.1f".format(maxD)}px")
			}
			println("%-10s %s".format(name, parts.joinToString("  ")))
		}
	}
}
