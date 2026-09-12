package io.github.psd2live.core

/**
 * Software fallback for the chest jiggle (`ParamBust`), ported from Auto_Vtb's
 * `bust bounce` spring. Used only when the Cubism SDK is unavailable; the native
 * preview drives `ParamBust` through the exported `PhysicsChest` pendulum instead.
 *
 * Auto_Vtb's model:
 *   bustTgt   = (breath*3 - angleY*6 + body*4) * faceScale
 *   spring    = kk=140, cc=4.2  (underdamped, so it overshoots on head turns)
 *   bounce.dy = -(bounce.x - bustTgt) * 3   (the spring's tracking lag = "jiggle")
 *   applied   = bounce.dy * e.bust (default 2.5) * gaussian(chest)
 *
 * The lag (not the position) is the output, so the chest settles to zero on a
 * steady pose and only bounces while the target changes (breathing, nodding, sway).
 */
internal class BustDynamics {
    var value: Float = 0f
        private set
    private var position = 0f
    private var velocity = 0f

    fun advance(breath: Float, angleY: Float, body: Float, deltaTime: Float, enabled: Boolean, amp: Float = 1f): Float {
        if (!enabled) {
            reset()
            return 0f
        }
        val dt = deltaTime.coerceIn(0.001f, 0.05f)
        val target = breath.coerceIn(0f, 1f) * 3f -
            angleY.coerceIn(-1f, 1f) * 6f +
            body.coerceIn(-1f, 1f) * 4f
        val kk = 140f
        val cc = 4.2f
        val acceleration = -kk * (position - target) - cc * velocity
        velocity += acceleration * dt
        position += velocity * dt
        // bounce.dy = -(position - target) * 3, then scaled by e.bust (~2.5 * amp) and mapped to -1..1.
        val lag = -(position - target) * 3f * 2.5f * amp.coerceIn(0f, 4f)
        value = (lag * 0.02f).coerceIn(-1f, 1f)
        return value
    }

    fun reset() {
        value = 0f
        position = 0f
        velocity = 0f
    }
}
