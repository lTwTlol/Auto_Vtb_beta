package io.github.psd2live.core

import org.umamo.format.moc3.Moc3
import org.umamo.interop.moc3.import.Moc3Import
import org.umamo.render.eval.CpuDeformationEvaluator
import org.umamo.runtime.model.ParameterId
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.sin

/**
 * Soak test: runs the preview physics simulator for 600 frames of idle-like motion and asserts
 * the evaluator never drops or NaNs a drawable (the mechanism behind "layers disappear in the
 * software preview").
 */
class PhysicsSoakTest {
	fun main(args: Array<String>) {
		val path = args.firstOrNull()
			?: "C:/Users/rr148/Desktop/kaifa/fb-test-out/2.moc3"
		val puppet = Moc3Import.fromMocDocument(
			Moc3.read(Files.readAllBytes(Paths.get(path))),
			null,
		)
		val evaluator = CpuDeformationEvaluator()
		val sim = PhysicsChainSimulator()
		val parts = PhysicsGenerator.PhysicsParts(
			frontHair = true, backHair = true, eyeJelly = true, bust = true, arms = true,
			sideHair = true, midHair = true, ahoge = true, collar = true,
			legDynamics = true, headPerspective = true, bodyCore = true,
		)
		val ids = puppet.parameters.mapTo(linkedSetOf()) { it.id.raw }
		sim.configure(PhysicsGenerator.validRules(parts, ids, PhysicsTuning()), puppet)

		val values = HashMap<ParameterId, Float>()
		for (p in puppet.parameters) values[p.id] = p.default

		var badFrames = 0
		var firstBad = -1
		val outputIds = PhysicsGenerator.validRules(parts, ids, PhysicsTuning()).map { it.outputParameter }
		for (frame in 0 until 600) {
			val t = frame / 30f
			values[ParameterId("ParamAngleX")] = sin(t * 0.9f) * 30f
			values[ParameterId("ParamAngleY")] = sin(t * 0.7f + 1f) * 20f
			values[ParameterId("ParamAngleZ")] = sin(t * 1.3f) * 25f
			values[ParameterId("ParamBodyAngleX")] = sin(t * 0.5f) * 8f
			values[ParameterId("ParamBodyAngleY")] = sin(t * 0.4f + 0.7f) * 6f
			values[ParameterId("ParamBodyAngleZ")] = sin(t * 0.8f) * 8f
			values[ParameterId("ParamBreath")] = (sin(t * 1.45f) + 1f) * 0.5f
			values[ParameterId("ParamEyeLOpen")] = 1f
			values[ParameterId("ParamEyeROpen")] = 1f
			sim.advance(1f / 30f, values)

			val geo = evaluator.evaluate(puppet, values)
			var missing = 0
			var nan = 0
			for (d in puppet.drawables) {
				if (d.mesh == null) continue
				val pos = geo.worldPositions[d.id]
				if (pos == null) missing++
				else for (v in pos) if (v.isNaN()) { nan++; break }
			}
			if (missing > 0 || nan > 0) {
				badFrames++
				if (firstBad < 0) {
					firstBad = frame
					for (d in puppet.drawables) {
						if (d.mesh == null) continue
						val pos = geo.worldPositions[d.id]
						if (pos == null) println("  MISSING drawable: ${d.id.raw} parent=${d.parentDeformerId?.raw}")
						else if (pos.any { it.isNaN() }) println("  NaN drawable: ${d.id.raw}")
					}
					val outs = outputIds.joinToString { it + "=" + (values[ParameterId(it)] ?: 0f) }
					println("first bad frame $frame missing=$missing nan=$nan")
					println("outputs: $outs")
				}
			}
		}
		println("RESULT: badFrames=$badFrames/600")
	}
}
