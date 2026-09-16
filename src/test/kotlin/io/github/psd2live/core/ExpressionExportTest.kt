package io.github.psd2live.core

import io.github.psd2live.agent.WorkspaceSourceArt
import io.github.psd2live.agent.WorkspaceSourceLayer
import io.github.psd2live.ui.state.PSD2LiveState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.float
import org.umamo.format.art.*
import org.umamo.format.moc3.Moc3
import kotlin.test.*

/**
 * The face-expression presets must survive the export: each preset ships as a Cubism
 * `.exp3.json` next to the moc and is registered in the model3 manifest, which is what makes
 * them show up as triggerable expressions in VTube Studio and other Cubism runtimes.
 */
class ExpressionExportTest {

	private fun dummySourceArt(): SourceArt {
		val width = 256
		val height = 256
		val rgba = ByteArray(width * height * 4) { index ->
			if (index % 4 == 3) 255.toByte() else 128.toByte()
		}
		val layer = WorkspaceSourceLayer(
			id = LayerId("layer1"),
			name = "face",
			groupPath = "",
			kind = SourceLayerKind.Raster,
			visible = true,
			order = 0,
			bounds = LayerBounds(0, 0, width, height),
			opacity = 1f,
			clipped = false,
			blend = LayerBlend.Normal,
			channelMask = ChannelMask.ALL,
			raster = LayerRaster(width, height, rgba),
			sourceAssetId = null,
			sourceSpatialReferenceId = null,
			derived = false,
		)
		return WorkspaceSourceArt(width, height, listOf(layer), emptyList())
	}

	@Test
	fun presetsExportAsExp3FilesRegisteredInTheManifest() {
		val pipeline = PSD2LivePipeline()
		val preview = pipeline.buildPreview(dummySourceArt(), PipelineConfig(atlasSize = 512))
		val manifestBytes = preview.runtimeBundle.assets.first { it.path.endsWith(".model3.json") }.bytes
		val manifest = Moc3.readModel3(manifestBytes.decodeToString())

		val refs = assertNotNull(manifest.fileReferences.expressions, "manifest must register the expression presets")
		assertEquals(ExpressionPresets.all.map { "psd2live-preview.${it.key}.exp3.json" }.toSet(), refs.map { it.file }.toSet())
		val assetNames = preview.runtimeBundle.assets.map { it.path }.toSet()
		assertTrue(assetNames.containsAll(refs.map { it.file }), "every registered expression file must ship in the bundle")
	}

	@Test
	fun expressionFilesEncodeAddDeltasAgainstDefaults() {
		val available = StandardParameters.all.map { it.id.raw }.toSet()
		val defaultOf = StandardParameters.all.associate { it.id.raw to it.default }
		for (preset in ExpressionPresets.all) {
			val json = Json.parseToJsonElement(requireNotNull(ExpressionPresets.exp3Json(preset, available))).jsonObject
			assertEquals("Live2D Expression", json.getValue("Type").jsonPrimitive.content, preset.key)
			val byId = json.getValue("Parameters").jsonArray.associate {
				it.jsonObject.getValue("Id").jsonPrimitive.content to it.jsonObject
			}
			val expectedDeltas = preset.assignments(available)
				.map { (id, value) -> id to value - defaultOf.getValue(id) }
				.filter { it.second != 0f }
				.toMap()
			assertEquals(expectedDeltas.keys, byId.keys, preset.key)
			for ((id, delta) in expectedDeltas) {
				assertEquals(delta, byId.getValue(id).getValue("Value").jsonPrimitive.float, preset.key)
				assertEquals("Add", byId.getValue(id).getValue("Blend").jsonPrimitive.content, preset.key)
			}
		}
	}

	@Test
	fun defaultPoseExportsAnEmptyParameterList() {
		val neutral = ExpressionPresets.byKey("neutral")!!
		val available = StandardParameters.all.map { it.id.raw }.toSet()
		val json = Json.parseToJsonElement(requireNotNull(ExpressionPresets.exp3Json(neutral, available))).jsonObject
		assertTrue(json.getValue("Parameters").jsonArray.isEmpty(), "neutral equals the default pose, so it carries no deltas")
	}

	@Test
	fun presetsKeepOnlyParametersTheRigCarries() {
		val winkL = ExpressionPresets.byKey("winkL")!!
		val json = Json.parseToJsonElement(
			requireNotNull(ExpressionPresets.exp3Json(winkL, setOf("ParamEyeLOpen", "ParamEyeROpen"))),
		).jsonObject
		// eyeR stays at its default (1), so only the closing left eye carries a delta.
		val ids = json.getValue("Parameters").jsonArray.map { it.jsonObject.getValue("Id").jsonPrimitive.content }
		assertEquals(listOf("ParamEyeLOpen"), ids)
	}

	@Test
	fun exportToggleDropsTheExpressionFiles() {
		val pipeline = PSD2LivePipeline()
		val base = pipeline.buildPreview(dummySourceArt(), PipelineConfig(atlasSize = 512))
		val updated = pipeline.updateRuntimeBundle(base, base.config.copy(exportExpressions = false))
		val manifestBytes = updated.runtimeBundle.assets.first { it.path.endsWith(".model3.json") }.bytes
		val manifest = Moc3.readModel3(manifestBytes.decodeToString())
		assertNull(manifest.fileReferences.expressions, "expression files must be dropped when the toggle is off")
	}

	@Test
	fun stateBuildConfigGatesExpressionExport() {
		assertTrue(PSD2LiveState().buildConfig().exportExpressions)
		assertFalse(PSD2LiveState(exportExpressions = false).buildConfig().exportExpressions)
		assertFalse(PSD2LiveState(meshOnly = true).buildConfig().exportExpressions, "mesh-only suppresses expressions")
	}
}
