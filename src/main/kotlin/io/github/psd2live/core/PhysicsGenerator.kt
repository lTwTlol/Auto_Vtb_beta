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

	/**
	 * Which secondary physics chains exist in the rig.  Hair-layer chains (side/mid/ahoge) and
	 * body-driven cloth chains (skirt/legs/collar) reproduce the pendulum recipes measured from a
	 * mature production rig: tapered multi-node chains for layered hair, uniform low-mobility
	 * chains for cloth, and an over-accelerated bouncy chain for the ahoge.
	 */
	data class PhysicsParts(
		val frontHair: Boolean = false,
		val backHair: Boolean = false,
		val eyeJelly: Boolean = false,
		val bust: Boolean = false,
		val arms: Boolean = false,
		val sideHair: Boolean = false,
		val midHair: Boolean = false,
		val ahoge: Boolean = false,
		val skirt: Boolean = false,
		val legs: Boolean = false,
		val collar: Boolean = false,
		/** 透视 lag chains: AngleX/Y/Z -> HeadOX/OY/OZ (production-reference recipes). */
		val headPerspective: Boolean = false,
		/** Slow leg-ground chains: ↓X2-腿 / ↓Z1.1腿 / ↓Z2.2腿. */
		val legDynamics: Boolean = false,
		/** 重心Z body counter-shift chain. */
		val bodyCore: Boolean = false,
	)

	internal fun rules(parts: PhysicsParts, tuning: PhysicsTuning = PhysicsTuning()): List<PhysicsRule> = buildList {
		if (parts.frontHair) {
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
		if (parts.backHair) {
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
		if (parts.eyeJelly) {
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
						VertexRule(8f, 0.88f, 0.18f, 1.9f, 8f),
						VertexRule(16f, 0.80f, 0.32f, 2.2f, 8f),
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
		if (parts.bust) {
			// 九轴 chest: two independent pendulums feed a 3x3 (nine-keyform) warp deformer.
			//   PhysicsChestX -> ParamBustX (horizontal sway: yaw / roll / body roll)
			//   PhysicsChestY -> ParamBust  (vertical jelly: pitch / body pitch / breath)
			// Each is a three-link underdamped chain so the breast overshoots and settles like
			// gelatin instead of tracking the pose.  Only parameters VTube Studio's face capture
			// actually drives are weighted strongly; ParamBreath stays a weak secondary.
			add(
				multiPendulumRule(
					id = "PhysicsChestX",
					name = tr("model.physics.bustX"),
					outputParameter = "ParamBustX",
					outputScale = 1f * tuning.bustAmp,
					outputVertexIndex = 2,
					inputs = listOf(
						InputRule("ParamBodyAngleX", 60f, InputType.X, reflect = true),
						InputRule("ParamBodyAngleY", 40f, InputType.X),
						InputRule("ParamBodyAngleZ", 60f, InputType.ANGLE),
						InputRule("ParamBreath", 9f, InputType.ANGLE),
					),
					vertices = listOf(
						floatArrayOf(1f, 1f, 0.9f),
						floatArrayOf(0.9f, 0.86f, 0.9f),
						floatArrayOf(0.9f, 0.87f, 0.9f),
						floatArrayOf(0.9f, 0.89f, 0.9f),
						floatArrayOf(0.9f, 0.88f, 0.9f),
					),
					angleMaximum = 30f,
				),
			)
			add(
				multiPendulumRule(
					id = "PhysicsChestY",
					name = tr("model.physics.bustY"),
					outputParameter = "ParamBust",
					outputScale = 1f * tuning.bustAmp,
					outputVertexIndex = 2,
					inputs = listOf(
						InputRule("ParamBodyAngleX", 60f, InputType.X),
						InputRule("ParamBodyAngleY", 40f, InputType.X),
						InputRule("ParamBreath", 9f, InputType.ANGLE),
					),
					vertices = listOf(
						floatArrayOf(1f, 1f, 0.9f),
						floatArrayOf(0.9f, 0.86f, 0.9f),
						floatArrayOf(0.9f, 0.87f, 0.9f),
						floatArrayOf(0.9f, 0.89f, 0.9f),
						floatArrayOf(0.9f, 0.88f, 0.9f),
					),
					angleMaximum = 30f,
				),
			)
		}
		if (parts.arms) {
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
		// --- Secondary chains (measured recipes) ---
		if (parts.sideHair) {
			// Tapered chain: each node softer than the one above it, so the tips trail the roots.
			add(
				multiPendulumRule(
					id = "PhysicsHairSide",
					name = tr("model.physics.sideHair"),
					outputParameter = "ParamHairSide",
					outputScale = 1f,
					inputs = listOf(
						InputRule("ParamAngleX", 40f, InputType.X),
						InputRule("ParamAngleZ", 40f, InputType.ANGLE),
					),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.93f, 0.91f, 0.80f),
						floatArrayOf(0.93f, 0.92f, 0.87f),
						floatArrayOf(0.93f, 0.87f, 0.78f),
					),
					positions = floatArrayOf(0f, 12.6f, 23f, 32.3f),
					radii = floatArrayOf(0f, 12.6f, 10.4f, 9.3f),
					angleMaximum = 10f,
				),
			)
		}
		if (parts.midHair) {
			// Inner hair layer: uniform and soft, the slowest responder of the hair stack.
			add(
				multiPendulumRule(
					id = "PhysicsHairMid",
					name = tr("model.physics.midHair"),
					outputParameter = "ParamHairMid",
					outputScale = 1f,
					inputs = listOf(
						InputRule("ParamAngleX", 40f, InputType.X),
						InputRule("ParamAngleZ", 30f, InputType.ANGLE),
					),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.90f, 0.80f, 0.80f),
						floatArrayOf(0.90f, 0.80f, 0.80f),
					),
					positions = floatArrayOf(0f, 8f, 16f),
					radii = floatArrayOf(0f, 8f, 8f),
					angleMaximum = 10f,
				),
			)
		}
		if (parts.ahoge) {
			// Bouncy: acceleration > 1 on every node plus a x2 output scale, so the cowlick
			// whips and settles instead of drifting with the head.
			add(
				multiPendulumRule(
					id = "PhysicsAhoge",
					name = tr("model.physics.ahoge"),
					outputParameter = "ParamAhoge",
					outputScale = 2f,
					inputs = listOf(
						InputRule("ParamAngleX", 30f, InputType.X),
						InputRule("ParamAngleZ", 40f, InputType.ANGLE),
					),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.935f, 0.86f, 1.05f),
						floatArrayOf(0.935f, 0.87f, 1.03f),
						floatArrayOf(0.935f, 0.89f, 1.00f),
						floatArrayOf(0.935f, 0.88f, 0.80f),
					),
					angleMaximum = 15f,
				),
			)
		}
		if (parts.skirt) {
			// 裙子物理X/Y - production recipe: uniform heavily-damped nodes, output read from
			// the SECOND node so the hem trails smoothly, Y chain coupled to breath.
			add(
				multiPendulumRule(
					id = "PhysicsSkirtX",
					name = tr("model.physics.skirt"),
					outputParameter = "ParamSkirtSwing",
					outputScale = 1f,
					outputVertexIndex = 2,
					inputs = listOf(
						InputRule("ParamBodyAngleX", 60f, InputType.X),
						InputRule("ParamBodyAngleZ", 60f, InputType.ANGLE),
					),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.90f, 0.80f, 0.90f),
						floatArrayOf(0.90f, 0.80f, 0.90f),
						floatArrayOf(0.90f, 0.80f, 0.90f),
						floatArrayOf(0.90f, 0.80f, 0.90f),
					),
					angleMaximum = 30f,
				),
			)
			add(
				multiPendulumRule(
					id = "PhysicsSkirtY",
					name = tr("model.physics.skirtY"),
					outputParameter = "ParamSkirtSwingY",
					outputScale = 0.811f,
					outputVertexIndex = 1,
					inputs = listOf(
						InputRule("ParamBodyAngleY", 84f, InputType.X),
						InputRule("ParamBreath", 9f, InputType.ANGLE),
					),
					vertices = listOf(
						floatArrayOf(1f, 0.80f, 0.90f),
						floatArrayOf(0.935f, 0.80f, 0.90f),
						floatArrayOf(0.935f, 0.80f, 0.90f),
						floatArrayOf(0.935f, 0.80f, 0.90f),
						floatArrayOf(0.935f, 0.80f, 0.90f),
					),
					angleMaximum = 30f,
				),
			)
		}
		if (parts.collar) {
			// 领结: low-delay light ribbon, quick to move and quick to stop.
			add(
				multiPendulumRule(
					id = "PhysicsCollar",
					name = tr("model.physics.collar"),
					outputParameter = "ParamCollarSwing",
					outputScale = 1.5f,
					inputs = listOf(
						InputRule("ParamBodyAngleX", 60f, InputType.X),
						InputRule("ParamBodyAngleZ", 46f, InputType.ANGLE),
					),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.90f, 0.70f, 1.00f),
						floatArrayOf(0.90f, 0.70f, 1.00f),
						floatArrayOf(0.90f, 0.70f, 1.00f),
						floatArrayOf(0.90f, 0.70f, 1.00f),
					),
					angleMaximum = 12f,
				),
			)
		}
		// --- 透视 perspective lag chains (production recipes, verbatim) ---
		if (parts.headPerspective) {
			add(
				multiPendulumRule(
					id = "PhysicsHeadOX",
					name = tr("model.physics.headOX"),
					outputParameter = "ParamHeadOX",
					outputScale = 30f,
					inputs = listOf(
						InputRule("ParamAngleX", 100f, InputType.ANGLE),
						InputRule("ParamAngleZ", 62f, InputType.X),
					),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.83f, 0.7f, 0.79f),
					),
					positions = floatArrayOf(0f, 20f),
					radii = floatArrayOf(0f, 20f),
					angleMaximum = 57.3f,
				),
			)
			add(
				multiPendulumRule(
					id = "PhysicsHeadOY",
					name = tr("model.physics.headOY"),
					outputParameter = "ParamHeadOY",
					outputScale = 30f,
					inputs = listOf(InputRule("ParamAngleY", 100f, InputType.ANGLE)),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.4f, 1.25f, 1.85f),
					),
					positions = floatArrayOf(0f, 10f),
					radii = floatArrayOf(0f, 10f),
					angleMaximum = 57.3f,
				),
			)
			add(
				multiPendulumRule(
					id = "PhysicsHeadOZ",
					name = tr("model.physics.headOZ"),
					outputParameter = "ParamHeadOZ",
					outputScale = 30f,
					inputs = listOf(InputRule("ParamAngleZ", 100f, InputType.ANGLE)),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.4f, 1.25f, 1.85f),
					),
					positions = floatArrayOf(0f, 10f),
					radii = floatArrayOf(0f, 10f),
					angleMaximum = 57.3f,
				),
			)
		}
		// --- slow leg-ground chains: heavy damping + tail output = smooth drag, never jelly ---
		if (parts.legDynamics) {
			add(
				multiPendulumRule(
					id = "PhysicsLegX2",
					name = tr("model.physics.legX2"),
					outputParameter = "ParamLegX2",
					outputScale = 57.3f,
					inputs = listOf(
						InputRule("ParamBodyAngleX", 100f, InputType.ANGLE, reflect = true),
						InputRule("ParamBodyAngleZ", 6f, InputType.X, reflect = true),
					),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.65f, 0.6f, 0.9f),
					),
					positions = floatArrayOf(0f, 16f),
					radii = floatArrayOf(0f, 16f),
					angleMaximum = 30f,
				),
			)
			add(
				multiPendulumRule(
					id = "PhysicsLegZ11",
					name = tr("model.physics.legZ11"),
					outputParameter = "ParamLegZ11",
					outputScale = 10f,
					inputs = listOf(InputRule("ParamBodyAngleZ", 100f, InputType.ANGLE)),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.7f, 1.31f, 0.81f),
					),
					angleMaximum = 30f,
				),
			)
			add(
				multiPendulumRule(
					id = "PhysicsLegZ22",
					name = tr("model.physics.legZ22"),
					outputParameter = "ParamLegZ22",
					outputScale = 5f,
					outputVertexIndex = 2,
					inputs = listOf(InputRule("ParamBodyAngleZ", 45f, InputType.ANGLE, reflect = true)),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.9f, 0.98f, 1f),
						floatArrayOf(0.87f, 0.86f, 0.63f),
					),
					positions = floatArrayOf(0f, 8f, 16f),
					radii = floatArrayOf(0f, 8f, 8f),
					angleMaximum = 30f,
				),
			)
		}
		// --- 重心Z: the torso counter-shifts against body Z/X for grounded weight ---
		if (parts.bodyCore) {
			add(
				multiPendulumRule(
					id = "PhysicsBodyCogZ",
					name = tr("model.physics.cogZ"),
					outputParameter = "ParamBodyCogZ",
					outputScale = 30f,
					outputVertexIndex = 1,
					inputs = listOf(
						InputRule("ParamBodyAngleZ", 50f, InputType.ANGLE, reflect = true),
						InputRule("ParamBodyAngleX", 50f, InputType.ANGLE),
					),
					vertices = listOf(
						floatArrayOf(1f, 1f, 1f),
						floatArrayOf(0.9f, 0.98f, 1f),
						floatArrayOf(0.87f, 0.86f, 0.63f),
					),
					positions = floatArrayOf(0f, 8f, 16f),
					radii = floatArrayOf(0f, 8f, 8f),
					angleMaximum = 30f,
				),
			)
		}
	}

	/** Legacy boolean entry point; the [PhysicsParts] overload carries the full chain set. */
	internal fun rules(
		hasFrontHair: Boolean,
		hasBackHair: Boolean,
		hasEyeJelly: Boolean,
		hasBust: Boolean = false,
		hasArms: Boolean = false,
		tuning: PhysicsTuning = PhysicsTuning(),
	): List<PhysicsRule> = rules(
		PhysicsParts(
			frontHair = hasFrontHair,
			backHair = hasBackHair,
			eyeJelly = hasEyeJelly,
			bust = hasBust,
			arms = hasArms,
		),
		tuning,
	)

	/**
	 * One pendulum per [vertices] row (mobility, delay, acceleration), spaced down the chain by
	 * [positions] with pendulum arm [radii] - PRODUCTION GEOMETRY, e.g. 5-node chains use
	 * positions [0,12,21,28,33] with radii [0,12,9,7,5].  The radius is the pendulum arm length:
	 * unit-scale radii (0..1) shrink the arm ~30x, pushing the natural oscillation frequency
	 * high enough to read as flutter in VTube Studio.  [outputVertexIndex] selects which node
	 * drives the output - production rigs read a MIDDLE node so the motion trails smoothly.
	 */
	private fun multiPendulumRule(
		id: String,
		name: String,
		outputParameter: String,
		outputScale: Float,
		inputs: List<InputRule>,
		vertices: List<FloatArray>,
		angleMaximum: Float,
		positions: FloatArray = floatArrayOf(0f, 12f, 21f, 28f, 33f).copyOf(vertices.size),
		radii: FloatArray = floatArrayOf(0f, 12f, 9f, 7f, 5f).copyOf(vertices.size),
		outputVertexIndex: Int = vertices.size - 1,
	): PhysicsRule = PhysicsRule(
		id = id,
		name = name,
		outputParameter = outputParameter,
		outputScale = outputScale,
		outputVertexIndex = outputVertexIndex,
		inputs = inputs,
		vertices = vertices.mapIndexed { index, (mobility, delay, acceleration) ->
			VertexRule(positions[index], mobility, delay, acceleration, radii[index])
		},
		positionMinimum = -10f,
		positionDefault = 0f,
		positionMaximum = 10f,
		angleMinimum = -angleMaximum,
		angleDefault = 0f,
		angleMaximum = angleMaximum,
	)

	internal fun validRules(
		parts: PhysicsParts,
		availableParameterIds: Set<String>,
		tuning: PhysicsTuning = PhysicsTuning(),
	): List<PhysicsRule> = rules(parts, tuning).filter { rule ->
		rule.outputParameter in availableParameterIds && rule.inputs.all { it.parameter in availableParameterIds }
	}

	internal fun validRules(
		hasFrontHair: Boolean,
		hasBackHair: Boolean,
		hasEyeJelly: Boolean,
		availableParameterIds: Set<String>,
		hasBust: Boolean = false,
		hasArms: Boolean = false,
		tuning: PhysicsTuning = PhysicsTuning(),
	): List<PhysicsRule> = validRules(
		PhysicsParts(
			frontHair = hasFrontHair,
			backHair = hasBackHair,
			eyeJelly = hasEyeJelly,
			bust = hasBust,
			arms = hasArms,
		),
		availableParameterIds,
		tuning,
	)

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
			VertexRule(length * 2f, mobility / soft.coerceIn(0.1f, 4f), delay * soft.coerceIn(0.1f, 4f), acceleration, length * 2f),
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
			VertexRule(20f, 0.6f, 0.4f, 1.6f, 20f),
		),
		positionMinimum = -1f,
		positionDefault = 0f,
		positionMaximum = 1f,
		angleMinimum = -30f * amp.coerceIn(0f, 4f),
		angleDefault = 0f,
		angleMaximum = 30f * amp.coerceIn(0f, 4f),
	)

	fun generate(hasFrontHair: Boolean, hasBackHair: Boolean, hasEyeJelly: Boolean = false, hasBust: Boolean = false): String? {
		return generate(
			PhysicsParts(frontHair = hasFrontHair, backHair = hasBackHair, eyeJelly = hasEyeJelly, bust = hasBust),
			null,
		)
	}

	fun generate(
		parts: PhysicsParts,
		availableParameterIds: Set<String>?,
		custom: List<RigPhysicsEdit> = emptyList(),
		tuning: PhysicsTuning = PhysicsTuning(),
	): String? {
		val presets = if (availableParameterIds == null) {
			rules(parts, tuning)
		} else {
			validRules(parts, availableParameterIds, tuning)
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
