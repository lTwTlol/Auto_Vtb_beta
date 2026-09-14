package io.github.psd2live.core

import io.github.psd2live.i18n.tr
import kotlinx.serialization.json.JsonPrimitive
/** Live2D physics3 presets for hair pendulums and blink-driven pupil squash/stretch. */
object PhysicsGenerator {
	internal enum class InputType(val jsonName: String) { X("X"), ANGLE("Angle") }

	internal data class InputRule(
		val parameter: String,
		val weight: Float,
		val type: InputType,
		val reflect: Boolean = false,
	)

	internal data class VertexRule(
		val y: Float,
		val mobility: Float,
		val delay: Float,
		val acceleration: Float,
		val radius: Float,
	)

	internal data class PhysicsRule(
		val id: String,
		val name: String,
		val outputParameter: String,
		val outputScale: Float,
		val outputVertexIndex: Int,
		val inputs: List<InputRule>,
		val vertices: List<VertexRule>,
		val positionMinimum: Float,
		val positionDefault: Float,
		val positionMaximum: Float,
		val angleMinimum: Float,
		val angleDefault: Float,
		val angleMaximum: Float,
	)

	internal fun rules(hasFrontHair: Boolean, hasBackHair: Boolean, hasEyeJelly: Boolean, hasBust: Boolean = false, hasArms: Boolean = false, tuning: PhysicsTuning = PhysicsTuning()): List<PhysicsRule> = buildList {
		if (hasFrontHair) {
			add(
				hairRule(
					id = "PhysicsHairFront",
					name = tr("model.physics.frontHair"),
					outputParameter = "ParamHairFront",
					outputScale = 1.522f,
					length = 7.9f,
					mobility = 0.77f,
					delay = 1.45f,
					acceleration = 0.8f,
					angleMinimum = -10f,
					angleMaximum = 10f,
					amp = tuning.frontHairAmp,
					soft = tuning.frontHairSoft,
				),
			)
		}
		if (hasBackHair) {
			add(
				hairRule(
					id = "PhysicsHairBack",
					name = tr("model.physics.backHair"),
					outputParameter = "ParamHairBack",
					outputScale = 2.061f,
					length = 15f,
					mobility = 0.95f,
					delay = 0.8f,
					acceleration = 1.5f,
					angleMinimum = -30f,
					angleMaximum = 30f,
					amp = tuning.backHairAmp,
					soft = tuning.backHairSoft,
				),
			)
		}
		if (hasEyeJelly) {
			add(
				PhysicsRule(
					id = "PhysicsEyeJelly",
					name = tr("model.physics.eyeJelly"),
					outputParameter = "ParamEyeBallForm",
					outputScale = 0.32f,
					outputVertexIndex = 2,
					inputs = listOf(
						InputRule("ParamEyeLOpen", 50f, InputType.X),
						InputRule("ParamEyeROpen", 50f, InputType.X),
					),
					vertices = listOf(
						VertexRule(0f, 1f, 1f, 1f, 0f),
						VertexRule(1f, 0.88f, 0.18f, 1.9f, 1f),
						VertexRule(2f, 0.80f, 0.32f, 2.2f, 1f),
					),
					positionMinimum = -1f,
					positionDefault = 0f,
					positionMaximum = 1f,
					angleMinimum = -10f,
					angleDefault = 0f,
					angleMaximum = 10f,
				),
			)
		}
		if (hasBust) {
			// 九轴 chest: two independent pendulums feed a 3x3 (nine-keyform) warp deformer.
			//   PhysicsChestX -> ParamBustX (horizontal sway: yaw / roll / body roll)
			//   PhysicsChestY -> ParamBust  (vertical jelly: pitch / body pitch / breath)
			// Each is a three-link underdamped chain so the breast overshoots and settles like
			// gelatin instead of tracking the pose.  Only parameters VTube Studio's face capture
			// actually drives are weighted strongly; ParamBreath stays a weak secondary.
			add(
				PhysicsRule(
					id = "PhysicsChestX",
					name = tr("model.physics.bustX"),
					outputParameter = "ParamBustX",
					outputScale = 1f * tuning.bustAmp,
					outputVertexIndex = 2,
					inputs = listOf(
						InputRule("ParamAngleY", 35f, InputType.ANGLE, reflect = true),
						InputRule("ParamAngleZ", 15f, InputType.ANGLE),
						InputRule("ParamBodyAngleZ", 30f, InputType.ANGLE),
					),
					vertices = listOf(
						VertexRule(0f, 1f, 1f, 1f, 0f),
						VertexRule(0.55f, 0.85f, 0.5f, 3f, 0.7f),
						VertexRule(1f, 0.65f, 0.35f, 3.6f, 1f),
					),
					positionMinimum = -1f,
					positionDefault = 0f,
					positionMaximum = 1f,
					angleMinimum = -18f * tuning.bustAmp,
					angleDefault = 0f,
					angleMaximum = 18f * tuning.bustAmp,
				),
			)
			add(
				PhysicsRule(
					id = "PhysicsChestY",
					name = tr("model.physics.bustY"),
					outputParameter = "ParamBust",
					outputScale = 1f * tuning.bustAmp,
					outputVertexIndex = 2,
					inputs = listOf(
						InputRule("ParamAngleX", 30f, InputType.X),
						InputRule("ParamBodyAngleX", 20f, InputType.X),
						InputRule("ParamBreath", 8f, InputType.X),
					),
					vertices = listOf(
						VertexRule(0f, 1f, 1f, 1f, 0f),
						VertexRule(0.55f, 0.85f, 0.5f, 3f, 0.7f),
						VertexRule(1f, 0.65f, 0.35f, 3.6f, 1f),
					),
					positionMinimum = -1f,
					positionDefault = 0f,
					positionMaximum = 1f,
					angleMinimum = -18f * tuning.bustAmp,
					angleDefault = 0f,
					angleMaximum = 18f * tuning.bustAmp,
				),
			)
		}
		if (hasArms) {
			add(
				armRule(
					id = "PhysicsArmL",
					name = tr("model.physics.armL"),
					outputParameter = "ParamArmSwingL",
					reflect = true,
					amp = tuning.armSwingAmp,
				),
			)
			add(
				armRule(
					id = "PhysicsArmR",
					name = tr("model.physics.armR"),
					outputParameter = "ParamArmSwingR",
					reflect = false,
					amp = tuning.armSwingAmp,
				),
			)
		}
	}

	internal fun validRules(
		hasFrontHair: Boolean,
		hasBackHair: Boolean,
		hasEyeJelly: Boolean,
		availableParameterIds: Set<String>,
		hasBust: Boolean = false,
		hasArms: Boolean = false,
		tuning: PhysicsTuning = PhysicsTuning(),
	): List<PhysicsRule> = rules(hasFrontHair, hasBackHair, hasEyeJelly, hasBust, hasArms, tuning).filter { rule ->
		rule.outputParameter in availableParameterIds && rule.inputs.all { it.parameter in availableParameterIds }
	}

	private fun hairRule(
		id: String,
		name: String,
		outputParameter: String,
		outputScale: Float,
		length: Float,
		mobility: Float = 0.95f,
		delay: Float,
		acceleration: Float = 1.5f,
		angleMinimum: Float,
		angleMaximum: Float,
		amp: Float = 1f,
		soft: Float = 1f,
	): PhysicsRule = PhysicsRule(
		id = id,
		name = name,
		outputParameter = outputParameter,
		outputScale = outputScale * amp.coerceIn(0f, 4f),
		outputVertexIndex = 1,
		inputs = listOf(
			InputRule("ParamAngleX", 60f, InputType.X),
			InputRule("ParamAngleZ", 60f, InputType.ANGLE),
			InputRule("ParamBodyAngleX", 40f, InputType.X),
			InputRule("ParamBodyAngleZ", 40f, InputType.ANGLE),
		),
		vertices = listOf(
			VertexRule(0f, 1f, 1f, 1f, 0f),
			VertexRule(length, mobility / soft.coerceIn(0.1f, 4f), delay * soft.coerceIn(0.1f, 4f), acceleration, length),
		),
		positionMinimum = -10f,
		positionDefault = 0f,
		positionMaximum = 10f,
		angleMinimum = angleMinimum * amp.coerceIn(0f, 4f),
		angleDefault = 0f,
		angleMaximum = angleMaximum * amp.coerceIn(0f, 4f),
	)

	/**
	 * Shoulder-pivot arm pendulum: the root (shoulder) vertex is pinned while the hand vertex lags,
	 * so body lean produces a Live2D-style arm swing with inertia.  [reflect] mirrors the body-roll
	 * input so the left and right arms swing in opposite phase.
	 */
	private fun armRule(
		id: String,
		name: String,
		outputParameter: String,
		reflect: Boolean,
		amp: Float = 1f,
	): PhysicsRule = PhysicsRule(
		id = id,
		name = name,
		outputParameter = outputParameter,
		outputScale = 1f * amp.coerceIn(0f, 4f),
		outputVertexIndex = 1,
		inputs = listOf(
			InputRule("ParamBodyAngleX", 30f, InputType.X),
			InputRule("ParamBodyAngleZ", 50f, InputType.ANGLE, reflect = reflect),
		),
		vertices = listOf(
			VertexRule(0f, 1f, 1f, 1f, 0f),
			VertexRule(1f, 0.6f, 0.4f, 1.6f, 1f),
		),
		positionMinimum = -1f,
		positionDefault = 0f,
		positionMaximum = 1f,
		angleMinimum = -30f * amp.coerceIn(0f, 4f),
		angleDefault = 0f,
		angleMaximum = 30f * amp.coerceIn(0f, 4f),
	)

	fun generate(hasFrontHair: Boolean, hasBackHair: Boolean, hasEyeJelly: Boolean = false, hasBust: Boolean = false): String? {
		return generate(hasFrontHair, hasBackHair, hasEyeJelly, null, emptyList(), hasBust, false)
	}

	fun generate(
		hasFrontHair: Boolean,
		hasBackHair: Boolean,
		hasEyeJelly: Boolean,
		availableParameterIds: Set<String>?,
        custom: List<RigPhysicsEdit> = emptyList(),
        hasBust: Boolean = false,
        hasArms: Boolean = false,
        tuning: PhysicsTuning = PhysicsTuning(),
	): String? {
		val presets = if (availableParameterIds == null) {
			rules(hasFrontHair, hasBackHair, hasEyeJelly, hasBust, hasArms, tuning)
		} else {
			validRules(hasFrontHair, hasBackHair, hasEyeJelly, availableParameterIds, hasBust, hasArms, tuning)
		}
		val rules = mergeCustomRules(presets, custom, availableParameterIds)
		if (rules.isEmpty()) return null
		val settings = rules.map(::settingJson)
		val dictionary = rules.map { rule -> "{ \"Id\": ${JsonPrimitive(rule.id)}, \"Name\": ${JsonPrimitive(rule.name)} }" }
		return """
		{
		  "Version": 3,
		  "Meta": {
		    "PhysicsSettingCount": ${rules.size},
		    "TotalInputCount": ${rules.sumOf { it.inputs.size }},
		    "TotalOutputCount": ${rules.size},
		    "VertexCount": ${rules.sumOf { it.vertices.size }},
		    "EffectiveForces": { "Gravity": { "X": 0, "Y": -1 }, "Wind": { "X": 0, "Y": 0 } },
		    "PhysicsDictionary": [${dictionary.joinToString(",")}]
		  },
		  "PhysicsSettings": [${settings.joinToString(",")}]
		}
		""".trimIndent()
	}

    internal fun mergeCustomRules(presets: List<PhysicsRule>, custom: List<RigPhysicsEdit>, available: Set<String>?): List<PhysicsRule> {
        if (available != null) custom.forEach { it.validate(available) }
        require(custom.map { it.id }.distinct().size == custom.size) { "Duplicate physics IDs" }
        require(custom.map { it.outputParameter }.distinct().size == custom.size) { "Independent physics must use distinct outputs" }
        return presets.filterNot { p -> custom.any { it.id == p.id || it.outputParameter == p.outputParameter } } + custom.map { it.rule() }
    }

	private fun settingJson(rule: PhysicsRule): String {
		val inputs = rule.inputs.joinToString(",\n") { input ->
			"""    { "Source": { "Target": "Parameter", "Id": ${JsonPrimitive(input.parameter)} }, "Weight": ${input.weight}, "Type": "${input.type.jsonName}", "Reflect": ${input.reflect} }"""
		}
		val vertices = rule.vertices.joinToString(",\n") { vertex ->
			"""    { "Position": { "X": 0, "Y": ${vertex.y} }, "Mobility": ${vertex.mobility}, "Delay": ${vertex.delay}, "Acceleration": ${vertex.acceleration}, "Radius": ${vertex.radius} }"""
		}
		return """
		{
		  "Id": ${JsonPrimitive(rule.id)},
		  "Input": [
		$inputs
		  ],
		  "Output": [
		    { "Destination": { "Target": "Parameter", "Id": ${JsonPrimitive(rule.outputParameter)} }, "VertexIndex": ${rule.outputVertexIndex}, "Scale": ${rule.outputScale}, "Weight": 100, "Type": "Angle", "Reflect": false }
		  ],
		  "Vertices": [
		$vertices
		  ],
		  "Normalization": {
		    "Position": { "Minimum": ${rule.positionMinimum}, "Default": ${rule.positionDefault}, "Maximum": ${rule.positionMaximum} },
		    "Angle": { "Minimum": ${rule.angleMinimum}, "Default": ${rule.angleDefault}, "Maximum": ${rule.angleMaximum} }
		  }
		}
		""".trimIndent()
	}
}
