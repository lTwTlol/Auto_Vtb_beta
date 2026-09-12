package io.github.psd2live.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Normalized face-tracking snapshot, ported 1:1 from Auto_Vtb's `osf.py`.
 *
 * The app's animation layer consumes the fixed set of normalized fields
 * (`ax`, `ay`, `az`, `eL`, `eR`, `mo`, `mouthForm`, `ex`, `ey`) — the same
 * fields MediaPipe FaceMesh produces — so OpenSeeFace can reuse the exact
 * same animation code path.
 */
data class FaceTrackingSnapshot(
    val live: Boolean = false,
    val ax: Float = 0f,
    val ay: Float = 0f,
    val az: Float = 0f,
    val eL: Float = 1f,
    val eR: Float = 1f,
    val mo: Float = 0f,
    val mouthForm: Float = 0f,
    val ex: Float = 0f,
    val ey: Float = 0f,
)

/**
 * OpenSeeFace / VSeeFace UDP receiver.
 *
 * Two face-tracking sources are supported, both streamed over UDP to the same
 * port (default 11573):
 *
 *  1. VSeeFace / VMC protocol  — one JSON object per datagram (ARKit blendshape
 *     names plus a ``r`` head-rotation vector in radians).
 *  2. OpenSeeFace (facetracker) — a raw binary struct per datagram (floats).
 *
 * The receiver listens on the port, parses each packet into a compact snapshot,
 * and exposes the latest snapshot thread-safely to the UI.
 */
class FaceTrackingReceiver(
    private val host: String = "127.0.0.1",
    private val port: Int = 11573,
) : AutoCloseable {

    private val lock = Any()
    @Volatile private var latest: FaceTrackingSnapshot = FaceTrackingSnapshot()
    private var lastSeenNanos: Long = 0L
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var running = false
    @Volatile var error: String? = null
        private set

    fun start() {
        if (running) return
        try {
            val s = DatagramSocket(null)
            s.reuseAddress = true
            s.bind(InetSocketAddress(host, port))
            s.soTimeout = 500
            socket = s
        } catch (e: Exception) {
            error = "OpenSeeFace UDP bind failed on $host:$port (${e.message})"
            socket = null
            return
        }
        running = true
        thread(isDaemon = true, name = "face-tracking-udp") { loop() }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
    }

    override fun close() = stop()

    private fun loop() {
        val buffer = ByteArray(65536)
        while (running) {
            val s = socket ?: break
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                s.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                break
            }
            val data = packet.data.copyOf(packet.length)
            val snapshot = parse(data) ?: continue
            synchronized(lock) {
                latest = snapshot
                lastSeenNanos = System.nanoTime()
            }
        }
    }

    /** Latest snapshot, or a `live = false` snapshot if stale / never received. */
    fun snapshot(maxAgeMs: Long = 1000): FaceTrackingSnapshot {
        val (snapshot, seen) = synchronized(lock) { latest to lastSeenNanos }
        val ageMs = (System.nanoTime() - seen) / 1_000_000
        return if (snapshot.live && ageMs <= maxAgeMs) snapshot else FaceTrackingSnapshot(live = false)
    }

    private fun parse(data: ByteArray): FaceTrackingSnapshot? =
        if (data.firstOrNull() == '{'.code.toByte()) parseJson(data) else parseBinary(data)

    // ---- VSeeFace / VMC JSON -----------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    private fun parseJson(data: ByteArray): FaceTrackingSnapshot? {
        val root = runCatching { json.parseToJsonElement(data.decodeToString()).jsonObject }.getOrNull()
            ?: return null
        val inner = (root["i"] as? JsonObject) ?: root

        fun arr(key: String): List<Float>? =
            (inner[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toFloatOrNull() }

        fun first(key: String): Float? = arr(key)?.getOrNull(0)

        var out = FaceTrackingSnapshot(live = true)

        // head rotation (r = [rx, ry, rz], radians; x = pitch, y = yaw, z = roll)
        val r = arr("r")
        if (r != null && r.size >= 3) {
            val rx = r[0]; val ry = r[1]; val rz = r[2]
            out = out.copy(ax = clamp(-ry * SCALE_YAW), ay = clamp(rx * SCALE_PITCH), az = clamp(-rz * SCALE_ROLL))
        }

        val b = inner["b"] as? JsonArray

        // eye openness: prefer the dedicated `e` field, else 1 - eyeBlink blendshape
        var eyeL: Float? = null
        var eyeR: Float? = null
        val e = inner["e"]
        if (e is JsonObject) {
            eyeL = (e["l"] as? JsonArray)?.getOrNull(0)?.let { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
            eyeR = (e["r"] as? JsonArray)?.getOrNull(0)?.let { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
        } else if (e is JsonArray) {
            eyeL = (e.getOrNull(0) as? JsonArray)?.getOrNull(0)?.let { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
            eyeR = (e.getOrNull(1) as? JsonArray)?.getOrNull(0)?.let { (it as? JsonPrimitive)?.content?.toFloatOrNull() }
        }
        if (eyeL == null) eyeL = 1f - blend(b, 8)   // ARKit eyeBlinkLeft
        if (eyeR == null) eyeR = 1f - blend(b, 9)   // ARKit eyeBlinkRight
        out = out.copy(eL = clamp01(eyeL), eR = clamp01(eyeR))

        val mo = blend(b, 24)                        // ARKit jawOpen
        val smile = blend(b, 43, 44)                 // ARKit mouthSmileLeft/Right
        out = out.copy(mo = clamp01(mo), mouthForm = clamp(smile - blend(b, 31))) // funnel subtracts

        val lookOut = blend(b, 14, 15)               // eyeLookOutLeft/Right
        val lookIn = blend(b, 12, 13)                // eyeLookInLeft/Right
        val lookUp = blend(b, 16, 17)                // eyeLookUpLeft/Right
        val lookDn = blend(b, 10, 11)                // eyeLookDownLeft/Right
        out = out.copy(ex = clamp(lookOut - lookIn), ey = clamp(lookUp - lookDn))

        return out
    }

    private fun blend(arr: JsonArray?, vararg indices: Int): Float {
        if (arr == null) return 0f
        var acc = 0f
        var n = 0
        for (i in indices) {
            if (i in 0 until arr.size) {
                acc += (arr[i] as? JsonPrimitive)?.content?.toFloatOrNull() ?: 0f
                n++
            }
        }
        return if (n == 0) 0f else acc / n
    }

    // ---- OpenSeeFace binary ------------------------------------------------------
    //
    // facetracker.py packs one struct per detected face:
    //   <d i f f f f B f 4f 3f 3f>  header  (timestamp, id, w, h, blink_r, blink_l,
    //                                          success, pnp_error, quaternion*4,
    //                                          euler*3, translation*3)
    //   then   N * f                  landmark confidence (N landmarks)
    //   then 2N * f                  landmark x/y (packed y then x)
    //   then  70*3 * f                normalized 3D points (x, -y, -z)
    //   then  14 * f                  features (see facetracker.py `features` list)

    private companion object {
        const val SCALE_YAW = 1.6f
        const val SCALE_PITCH = 1.6f
        const val SCALE_ROLL = 1.6f
        val DEG2RAD = (Math.PI / 180.0).toFloat()

        const val MOUTH_OPEN_SCALE = 2.0f
        const val MOUTH_FORM_SCALE = 3.0f
        const val GAZE_SCALE = 2.5f

        const val FEATURE_COUNT = 14
        const val FEAT_MOUTH_OPEN = 12
        const val FEAT_CORNER_L = 8
        const val FEAT_CORNER_R = 10

        const val HEADER_SIZE = 73 // calcsize("<di4fBf4f3f3f")
        const val POINT_COUNT = 70
        const val POINTS_OFFSET_FROM_END = (POINT_COUNT * 3 + FEATURE_COUNT) * 4
        const val PUPIL_R = 66
        const val PUPIL_L = 67
        const val EYEBALL_R = 68
        const val EYEBALL_L = 69
    }

    private fun parseBinary(data: ByteArray): FaceTrackingSnapshot? {
        if (data.size < HEADER_SIZE + FEATURE_COUNT * 4) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        buf.getDouble()                    // time
        buf.getInt()                       // face_id
        buf.getFloat()                     // width
        buf.getFloat()                     // height
        val blinkR = buf.getFloat()
        val blinkL = buf.getFloat()
        val success = buf.get()            // unsigned byte
        buf.getFloat()                     // pnp_error
        repeat(4) { buf.getFloat() }       // quaternion
        val euler = FloatArray(3) { buf.getFloat() }
        repeat(3) { buf.getFloat() }       // translation

        val feats = FloatArray(FEATURE_COUNT)
        ByteBuffer.wrap(data, data.size - FEATURE_COUNT * 4, FEATURE_COUNT * 4)
            .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(feats)

        val qx = euler[0]; val qy = euler[1]; val qz = euler[2]
        val pitch = wrap180(-(qx + 180f))
        val yaw = wrap180(qy)
        val roll = wrap180(qz - 90f)

        val mouthOpen = feats[FEAT_MOUTH_OPEN]
        val smile = 0.5f * (feats[FEAT_CORNER_L] + feats[FEAT_CORNER_R])

        var out = FaceTrackingSnapshot(
            live = true,
            eL = clamp01(blinkL),
            eR = clamp01(blinkR),
            ax = clamp(-yaw * DEG2RAD * SCALE_YAW),
            ay = clamp(pitch * DEG2RAD * SCALE_PITCH),
            az = clamp(-roll * DEG2RAD * SCALE_ROLL),
            mo = clamp01(mouthOpen * MOUTH_OPEN_SCALE),
            mouthForm = clamp(smile * MOUTH_FORM_SCALE),
        )

        if (success.toInt() != 0) {
            val (exRaw, eyRaw) = gaze3d(data)
            out = out.copy(ex = clamp(exRaw * GAZE_SCALE), ey = clamp(eyRaw * GAZE_SCALE))
        } else {
            out = out.copy(ex = 0f, ey = 0f)
        }
        return out
    }

    /** Conjugate gaze from the packed 3D points (pupil minus eyeball centre, over depth). */
    private fun gaze3d(data: ByteArray): Pair<Float, Float> {
        if (data.size < POINTS_OFFSET_FROM_END + HEADER_SIZE) return 0f to 0f
        val base = data.size - POINTS_OFFSET_FROM_END
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        fun point(i: Int): Triple<Float, Float, Float> {
            val off = base + i * 12
            return Triple(buf.getFloat(off), buf.getFloat(off + 4), buf.getFloat(off + 8))
        }

        fun eye(pupil: Int, eyeball: Int): Pair<Float, Float> {
            val (px, py, pz) = point(pupil)
            val (ex, ey, ez) = point(eyeball)
            val depth = ez - pz // eyeball centre is behind the pupil -> positive
            if (depth <= 1e-6f) return 0f to 0f
            return (px - ex) / depth to (ey - py) / depth
        }

        val (hr, vr) = eye(PUPIL_R, EYEBALL_R)
        val (hl, vl) = eye(PUPIL_L, EYEBALL_L)
        return 0.5f * (hr + hl) to 0.5f * (vr + vl)
    }

    private fun wrap180(d: Float): Float {
        val m = d % 360f
        val v = if (m < 0f) m + 360f else m
        return if (v > 180f) v - 360f else v
    }

    private fun clamp(v: Float, a: Float = -1f, b: Float = 1f): Float = when {
        v < a -> a
        v > b -> b
        else -> v
    }

    private fun clamp01(v: Float): Float = clamp(v, 0f, 1f)
}
