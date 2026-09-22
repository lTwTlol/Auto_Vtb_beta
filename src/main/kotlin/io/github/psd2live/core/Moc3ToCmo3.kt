package io.github.psd2live.core

import org.umamo.format.cmo3.Cmo3
import org.umamo.format.cmo3.model.custom.CModelSource
import org.umamo.format.moc3.Moc3
import org.umamo.interop.cmo3.Cmo3Conversion
import org.umamo.interop.cmo3.Cmo3Import
import org.umamo.interop.moc3.import.Moc3Import
import org.umamo.render.restMeshesToCanvasSpace
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Converts a third-party runtime `.moc3` (+ sidecars) back into an editable `.cmo3` project,
 * reusing the app's own import/export machinery:
 *
 *   moc3 --Moc3.read--> MocDocument --Moc3Import--> PuppetModel
 *      --restMeshesToCanvasSpace--> export puppet --Cmo3Conversion.freshCmo3--> Cmo3Model --Cmo3.write--> .cmo3
 *
 * The result is a RECONSTRUCTED project, not the author's original workspace: rig structure
 * (parameters, deformers, meshes, keyforms) carries over, while PSD layer history is gone and
 * deformer display names fall back to their format ids (moc3 does not store them).
 */
object Moc3ToCmo3 {
	data class ConvertedFile(val path: Path, val bytes: Long)

	data class Result(
		val files: List<ConvertedFile>,
		val notices: List<String>,
		val validation: List<String>,
	)

	/** PNG IHDR: 8-byte signature + 4 length + 4 "IHDR", then big-endian width/height. */
	private fun pngSize(bytes: ByteArray): Pair<Int, Int> {
		require(bytes.size > 24 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) { "not a PNG" }
		val width = ((bytes[16].toInt() and 0xFF) shl 24) or ((bytes[17].toInt() and 0xFF) shl 16) or
			((bytes[18].toInt() and 0xFF) shl 8) or (bytes[19].toInt() and 0xFF)
		val height = ((bytes[20].toInt() and 0xFF) shl 24) or ((bytes[21].toInt() and 0xFF) shl 16) or
			((bytes[22].toInt() and 0xFF) shl 8) or (bytes[23].toInt() and 0xFF)
		return width to height
	}

	fun convert(moc3Path: Path, outputDirectory: Path): Result {
		val moc3File = moc3Path.toAbsolutePath().normalize()
		require(moc3File.toString().endsWith(".moc3")) { "input must be a .moc3 file: $moc3File" }
		val baseName = moc3File.fileName.toString().removeSuffix(".moc3")
		val folder = moc3File.parent

		// Sidecars: prefer the sibling model3.json wiring; fall back to name-based siblings.
		val model3File = Files.list(folder).use { stream ->
			stream.filter { it.fileName.toString().endsWith(".model3.json") }.findFirst().orElse(null)
		}
		val manifest = model3File?.let { Moc3.readModel3(Files.readString(it)) }
		val cdi3File = manifest?.fileReferences?.displayInfo?.let { folder.resolve(it) }
			?.takeIf { Files.exists(it) }
			?: Files.list(folder).use { stream ->
				stream.filter { it.fileName.toString() == "$baseName.cdi3.json" }.findFirst().orElse(null)
			}
		val textureFiles = manifest?.fileReferences?.textures?.map { folder.resolve(it) }
			?: run {
				// No manifest: texture_XX.png under any sibling "<baseName>.*" directory, in index order.
				val found = mutableListOf<Path>()
				Files.list(folder).use { stream ->
					stream.filter { page -> Files.isDirectory(page) && page.fileName.toString().startsWith("$baseName.") }
						.forEach { dir -> dir.toFile().listFiles()?.forEach { file -> found.add(file.toPath()) } }
				}
				found.filter { it.fileName.toString().startsWith("texture_") }
					.sortedBy { it.fileName.toString() }
			}
		require(textureFiles.isNotEmpty()) { "no texture atlas pages found next to $moc3File" }
		for (texture in textureFiles) require(Files.exists(texture)) { "missing texture: $texture" }

		val document = Moc3.read(Files.readAllBytes(moc3File))
		val displayInfo = cdi3File?.let { Moc3.readCdi3(Files.readString(it)) }
		val puppet = Moc3Import.fromMocDocument(document, displayInfo)
		val exportPuppet = restMeshesToCanvasSpace(puppet)

		val pages = textureFiles.map { file ->
			val png = Files.readAllBytes(file)
			val (width, height) = pngSize(png)
			Cmo3Conversion.AtlasPage(png, width, height)
		}
		val pageIndexByDrawableId = exportPuppet.drawables.associate { drawable ->
			drawable.id.raw to drawable.texturePage
		}

		val converted = Cmo3Conversion.freshCmo3(
			puppet = exportPuppet,
			pages = pages,
			pageIndexByDrawableId = pageIndexByDrawableId,
			modelName = baseName,
			nowMillis = Instant.now().toEpochMilli(),
			obfuscateKey = 0x42,
		)
		val bytes = Cmo3.write(converted.model)

		Files.createDirectories(outputDirectory.toAbsolutePath().normalize())
		val outFile = outputDirectory.toAbsolutePath().normalize().resolve("$baseName.cmo3")
		Files.write(outFile, bytes)

		// Round-trip: re-read the produced container and re-import it, then compare shape.
		val validation = mutableListOf<String>()
		val source = Cmo3.read(bytes).root as? CModelSource
			?: error("cmo3 round-trip: root is not a CModelSource")
		val reimported = Cmo3Import.fromModelSource(source)
		fun shape(p: org.umamo.runtime.model.PuppetModel) =
			"${p.parameters.size}p/${p.drawables.size}d/${p.deformers.size}f/${p.parts.size}part/${p.glues.size}g"
		validation += "moc3   shape: ${shape(puppet)}"
		validation += "cmo3   shape: ${shape(reimported)}"
		if (reimported.parameters.size != puppet.parameters.size) validation += "WARN: parameter count differs"
		if (reimported.drawables.size != puppet.drawables.size) validation += "WARN: drawable count differs"
		if (reimported.deformers.size != puppet.deformers.size) validation += "WARN: deformer count differs"
		if (validation.none { it.startsWith("WARN") }) validation += "OK: shape preserved"

		return Result(
			files = listOf(ConvertedFile(outFile, bytes.size.toLong())),
			notices = converted.report.notices.map { it.toString() },
			validation = validation,
		)
	}
}
