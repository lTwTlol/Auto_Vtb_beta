package io.github.psd2live.core

import org.umamo.runtime.model.ParameterId
import org.umamo.runtime.model.PuppetModel

/**
 * Software pendulum-chain simulator for the in-app preview: runs the SAME [PhysicsGenerator.PhysicsRule]
 * set the exporter writes into physics3.json, so the preview shows the skirt / bust / legs /
 * perspective chains the way VTube Studio evaluates them (the previous preview only had a
 * hard-coded bust spring, which is why preview and VTS diverged).
 *
 * The integration is a per-node underdamped follow (spring toward the previous node, damping
 * scaled by node index, overshoot scaled by the node's Acceleration).  It is not frame-exact
 * against the official Cubism solver, but it reproduces the production character: heavy-damped
 * production geometry (positions 0..33, radii 0..12) trails smoothly and never flutters.
 */
class PhysicsChainSimulator {
	private class Chain(
		val rule: PhysicsGenerator.PhysicsRule,
		val nodes: FloatArray,
		val velocities: FloatArray,
		val inputParams: List<ParameterId>,
		val inputWeights: List<Float>,
		val inputReflect: List<Boolean>,
		val inputIsAngle: List<Boolean>,
		val outputParam: ParameterId,
		val outputScale: Float,
		val outputMin: Float,
		val outputMax: Float,
		val posRange: Float,
		val angleRange: Float,
	)

	private var chains: List<Chain> = emptyList()
	private var signature: Any? = null

	/** (Re)builds the simulated chains for [rules] whose output parameter exists in [puppet]. */
	internal fun configure(rules: List<PhysicsGenerator.PhysicsRule>, puppet: PuppetModel) {
		val signature: Any = rules.map { it.id } to puppet.drawables.size
		if (this.signature == signature) return
		val ranges = HashMap<String, Pair<Float, Float>>()
		for (parameter in puppet.parameters) ranges[parameter.id.raw] = Pair(parameter.min, parameter.max)
		val built = ArrayList<Chain>(rules.size)
		for (rule in rules) {
			val range = ranges[rule.outputParameter] ?: continue
			built.add(
				Chain(
					rule = rule,
					nodes = FloatArray(rule.vertices.size),
					velocities = FloatArray(rule.vertices.size),
					inputParams = rule.inputs.map { ParameterId(it.parameter) },
					inputWeights = rule.inputs.map { it.weight },
					inputReflect = rule.inputs.map { it.reflect },
					inputIsAngle = rule.inputs.map { it.type == PhysicsGenerator.InputType.ANGLE },
					outputParam = ParameterId(rule.outputParameter),
					outputScale = rule.outputScale,
					outputMin = range.first,
					outputMax = range.second,
					posRange = (rule.positionMaximum - rule.positionMinimum).coerceAtLeast(1e-4f),
					angleRange = (rule.angleMaximum - rule.angleMinimum).coerceAtLeast(1e-4f),
				),
			)
		}
		chains = built
		this.signature = signature
	}

	/** Advances the simulation and writes every chain's output parameter into [values]. */
	fun advance(dtSeconds: Float, values: MutableMap<ParameterId, Float>) {
		if (chains.isEmpty()) return
		val dt = dtSeconds.coerceIn(0.008f, 0.05f)
		val substeps = 4
		val h = dt / substeps
		for (chain in chains) {
			var input = 0f
			for (index in chain.inputParams.indices) {
				val raw = values[chain.inputParams[index]] ?: continue
				// 归一化：标准输入以 0 为中心对称，静止时必须映射到 0（旧公式减去半程再乘 2，
				// 静止时算出 -1 满偏，导致预览里所有物理参数静止值顶在极限）
				var normalized = if (chain.inputIsAngle[index]) {
					raw / (chain.angleRange / 2f)
				} else {
					raw / (chain.posRange / 2f)
				}
				if (chain.inputReflect[index]) normalized = -normalized
				input += normalized * (chain.inputWeights[index] / 100f)
			}
			repeat(substeps) {
				chain.nodes[0] = input
				chain.velocities[0] = 0f
				for (i in 1 until chain.nodes.size) {
					val stiffness = 90f * (0.5f + i.toFloat() / chain.nodes.size)
					val damping = (6f + i) * 1.6f
					val accel = chain.rule.vertices[i].acceleration
					val a = (chain.nodes[i - 1] - chain.nodes[i]) * stiffness - chain.velocities[i] * damping
					chain.velocities[i] += a * h * accel
					chain.nodes[i] += chain.velocities[i] * h
				}
			}
			values[chain.outputParam] = (chain.nodes[chain.rule.outputVertexIndex] * chain.outputScale)
				.coerceIn(chain.outputMin, chain.outputMax)
		}
	}

	fun reset() {
		for (chain in chains) {
			chain.nodes.fill(0f)
			chain.velocities.fill(0f)
		}
	}
}
