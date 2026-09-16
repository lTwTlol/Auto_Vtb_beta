package io.github.psd2live.core

import io.github.psd2live.i18n.tr

/**
 * One face-expression preset, shared by the in-app preview (locked parameter values) and the
 * MOC3 export (Cubism `.exp3.json` expression files).  Values are ABSOLUTE parameter values,
 * matching exactly what the preview shows.
 */
data class ExpressionPreset(
	val key: String,
	val eyeL: Float,
	val eyeR: Float,
	val brow: Float,
	val mouthOpen: Float,
	val mouthForm: Float,
) {
	/** The display name used in the UI and in the exported manifest's `Expressions` entries. */
	val displayName: String get() = tr("preset.$key")

	/** The (parameter id, value) assignments, kept to the ids this rig actually carries. */
	fun assignments(availableParameterIds: Set<String>): List<Pair<String, Float>> = listOf(
		StandardParameters.EYE_L_OPEN.raw to eyeL,
		StandardParameters.EYE_R_OPEN.raw to eyeR,
		StandardParameters.BROW_L_Y.raw to brow,
		StandardParameters.BROW_R_Y.raw to brow,
		StandardParameters.MOUTH_OPEN.raw to mouthOpen,
		StandardParameters.MOUTH_FORM.raw to mouthForm,
	).filter { it.first in availableParameterIds }

	/**
	 * The assignments as ADD-BLEND DELTAS against each parameter's default - the encoding the
	 * official Live2D sample models ship and the only one VTube Studio guarantees to apply on
	 * top of tracking, physics, and every other value provider (its Add/Multiply stack is
	 * applied after everything else).
	 */
	fun addDeltas(availableParameterIds: Set<String>): List<Pair<String, Float>> =
		assignments(availableParameterIds)
			.map { (id, value) -> id to (value - (ExpressionPresets.defaultValueByParameterId[id] ?: 0f)) }
			.filter { it.second != 0f }
}

/** The expression preset table and its `.exp3.json` writer. */
object ExpressionPresets {
	/** Fade times written into the `.exp3.json` files, the Cubism editor's export defaults. */
	const val FADE_IN_SECONDS: Float = 0.2f
	const val FADE_OUT_SECONDS: Float = 0.4f

	/** Neutral value each standard expression parameter rests at; the Add-delta baseline. */
	internal val defaultValueByParameterId: Map<String, Float> =
		StandardParameters.all.associate { it.id.raw to it.default }

	/** Key order here is the UI button order and the exported expression order. */
	val all: List<ExpressionPreset> = listOf(
		ExpressionPreset("neutral", 1f, 1f, 0f, 0f, 0f),
		ExpressionPreset("smile", 0f, 0f, 0.45f, 0f, 0.9f),
		ExpressionPreset("usume", 0.5f, 0.5f, 0.35f, 1f, 0.8f),
		ExpressionPreset("surprise", 1f, 1f, 1f, 0.75f, -0.1f),
		ExpressionPreset("jito", 0.4f, 0.4f, -0.6f, 0f, -0.4f),
		ExpressionPreset("winkL", 0f, 1f, 0.2f, 0.4f, 0.7f),
		ExpressionPreset("winkR", 1f, 0f, 0.2f, 0.4f, 0.7f),
	)

	private val byKey: Map<String, ExpressionPreset> = all.associateBy { it.key }

	fun byKey(key: String): ExpressionPreset? = byKey[key]

	/** Every parameter id the presets may reference, for locking the preview sliders. */
	val parameterIds: Set<org.umamo.runtime.model.ParameterId> = setOf(
		StandardParameters.EYE_L_OPEN,
		StandardParameters.EYE_R_OPEN,
		StandardParameters.BROW_L_Y,
		StandardParameters.BROW_R_Y,
		StandardParameters.MOUTH_OPEN,
		StandardParameters.MOUTH_FORM,
	)

	/**
	 * The Cubism `.exp3.json` text for [preset] in the official sample models' layout, or null
	 * when the rig carries none of the expression parameters.  Parameters are written as ADD
	 * deltas (see [ExpressionPreset.addDeltas]); a preset equal to the default pose exports an
	 * empty parameter list rather than a no-op write on every slider.
	 */
	fun exp3Json(preset: ExpressionPreset, availableParameterIds: Set<String>): String? {
		val assignments = preset.assignments(availableParameterIds)
		if (assignments.isEmpty()) return null
		val deltas = preset.addDeltas(availableParameterIds)
		val parameters = deltas.joinToString(",\n") { (id, value) ->
			"\t\t\t\t\t{\n" +
				"\t\t\t\t\t\t\"Id\": \"$id\",\n" +
				"\t\t\t\t\t\t\"Value\": ${value.toJson()},\n" +
				"\t\t\t\t\t\t\"Blend\": \"Add\"\n" +
				"\t\t\t\t\t}"
		}
		return """
			{
				"Type": "Live2D Expression",
				"FadeInTime": ${FADE_IN_SECONDS.toJson()},
				"FadeOutTime": ${FADE_OUT_SECONDS.toJson()},
				"Parameters": [
$parameters
				]
			}
		""".trimIndent()
	}

	private fun Float.toJson(): String =
		if (this == toInt().toFloat()) toInt().toString() else toString()
}
