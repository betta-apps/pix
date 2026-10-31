package io.betta.pix

import android.graphics.Bitmap
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.SystemClock
import android.os.Trace
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Develops each backdrop once into a texture (softness, halation, grain, warmth), then only pans across it, so
 * the grain stays fixed to the still and each frame costs a texture read or two. A new film fades in over the last.
 */
class LookRenderer : GLSurfaceView.Renderer {
    /** A slow camera move across one film: travelling from one side to the other, drifting slightly in or out. */
    private class Move(val zoomFrom: Float, val zoomTo: Float, val angle: Float) {
        companion object {
            fun random(): Move {
                val zoomIn = Random.nextBoolean()
                return Move(if (zoomIn) MIN_ZOOM else MAX_ZOOM, if (zoomIn) MAX_ZOOM else MIN_ZOOM, Random.nextFloat() * 2 * PI.toFloat())
            }
        }
    }

    /** A developed film on the GPU and its camera move. */
    private class Slot {
        var texture = 0
        var fbo = 0
        var shownAt = 0L
        var move = Move.random()
        var moveSeconds = 63f
        var ready = false
    }

    @Volatile private var pending: Bitmap? = null
    @Volatile private var pendingMoveSeconds = 63f
    @Volatile private var look = FilmLook.presets[1]
    @Volatile private var needsBake = false
    @Volatile var brightness = 1f

    private var current: Bitmap? = null
    private var bakeProgram = 0
    private var showProgram = 0
    private var imageTex = 0
    // The film fading in and the one it replaces, which keeps moving underneath until the fade is done.
    private var front = Slot()
    private var back = Slot()
    private var viewWidth = 1
    private var viewHeight = 1
    // The GPU's own timer, where it has one, for how long a bake takes it while a trace is being taken, one bake at a
    // time: read once it's done, a frame or so later, as the "bake GPU us" counter. 0 on a GPU without it.
    private var bakeTimer = 0
    private var timing = false

    private val triangle = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)).position(0) }

    /** Shows [bitmap] next, moving across it for [millis] plus the fade into the film after it. */
    fun show(bitmap: Bitmap, millis: Long) {
        pendingMoveSeconds = millis / 1000f + FADE_MILLIS / 1000f
        pending = bitmap
    }

    fun setLook(look: FilmLook) {
        this.look = look
        needsBake = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        bakeProgram = program(BAKE)
        showProgram = program(SHOW)
        val ids = IntArray(1)
        glGenTextures(1, ids, 0)
        imageTex = ids[0]
        bakeTimer = 0
        timing = false
        if (glGetString(GL_EXTENSIONS)?.contains("GL_EXT_disjoint_timer_query") == true) {
            glGenQueries(1, ids, 0)
            bakeTimer = ids[0]
        }
        front = slot()
        back = slot()
        // A new surface lost the films on the GPU; develop the current one again, without a fade.
        current?.let { if (pending == null) pending = it }
    }

    private fun slot() = Slot().apply {
        val ids = IntArray(2)
        glGenTextures(1, ids, 0)
        glGenFramebuffers(1, ids, 1)
        texture = ids[0]
        fbo = ids[1]
        glBindTexture(GL_TEXTURE_2D, texture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, BAKE_WIDTH, BAKE_HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        clampLinear(GL_LINEAR)
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.uptimeMillis()
        pending?.let {
            pending = null
            current = it
            traced("backdrop upload") { upload(it) }
            front = back.also { back = front }
            front.move = Move.random()
            front.moveSeconds = pendingMoveSeconds
            front.shownAt = now
            front.ready = true
            needsBake = true
        }
        if (needsBake && current != null) {
            needsBake = false
            val timed = bakeTimer != 0 && !timing && Trace.isEnabled()
            if (timed) glBeginQuery(GL_TIME_ELAPSED_EXT, bakeTimer)
            traced("backdrop bake") { bake(front) }
            if (timed) {
                glEndQuery(GL_TIME_ELAPSED_EXT)
                timing = true
            }
        }
        if (timing) readBakeTimer()
        glViewport(0, 0, viewWidth, viewHeight)
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        if (!front.ready) return

        val fade = ((now - front.shownAt) / FADE_MILLIS.toFloat()).coerceAtMost(1f)
        glUseProgram(showProgram)
        glUniform2f(glGetUniformLocation(showProgram, "uView"), viewWidth.toFloat(), viewHeight.toFloat())
        glUniform1f(glGetUniformLocation(showProgram, "uVig"), look.vignette)
        glUniform1f(glGetUniformLocation(showProgram, "uBright"), brightness)
        if (fade < 1f && back.ready) present(back, now, 1f)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        present(front, now, fade * fade * (3 - 2 * fade))
        glDisable(GL_BLEND)
    }

    private fun present(slot: Slot, now: Long, opacity: Float) {
        // Constant speed from the first frame, and still moving while the next film fades in over it.
        val t = ((now - slot.shownAt) / 1000f / slot.moveSeconds).coerceAtMost(1.1f)
        val zoom = slot.move.zoomFrom + (slot.move.zoomTo - slot.move.zoomFrom) * t
        // How far the image can shift at this zoom without showing an edge, leaving margin for running on past t = 1.
        val room = (0.5f - 0.5f / zoom) * 0.8f
        val along = 2 * t - 1
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, slot.texture)
        glUniform1f(glGetUniformLocation(showProgram, "uZoom"), zoom)
        glUniform2f(glGetUniformLocation(showProgram, "uPan"), cos(slot.move.angle) * room * along, sin(slot.move.angle) * room * along)
        glUniform1f(glGetUniformLocation(showProgram, "uAlpha"), opacity)
        draw(showProgram)
    }

    private fun upload(bitmap: Bitmap) {
        glBindTexture(GL_TEXTURE_2D, imageTex)
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0)
        glGenerateMipmap(GL_TEXTURE_2D)
        clampLinear(GL_LINEAR_MIPMAP_LINEAR)
    }

    private fun bake(slot: Slot) {
        val l = look
        glBindFramebuffer(GL_FRAMEBUFFER, slot.fbo)
        glViewport(0, 0, BAKE_WIDTH, BAKE_HEIGHT)
        glUseProgram(bakeProgram)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, imageTex)
        glUniform2f(glGetUniformLocation(bakeProgram, "uSize"), BAKE_WIDTH.toFloat(), BAKE_HEIGHT.toFloat())
        glUniform1f(glGetUniformLocation(bakeProgram, "uAmt"), l.grain)
        // Grain sizes were tuned on a 2112-wide bake seen at about 1.07x; scale them so the grain looks the same on screen.
        glUniform1f(glGetUniformLocation(bakeProgram, "uRad"), l.grainSize * BAKE_WIDTH / 2112f * 1.07f / ((MIN_ZOOM + MAX_ZOOM) / 2))
        glUniform1f(glGetUniformLocation(bakeProgram, "uSoft"), l.softness)
        glUniform1f(glGetUniformLocation(bakeProgram, "uHal"), l.halation)
        glUniform1f(glGetUniformLocation(bakeProgram, "uWarm"), l.warmth)
        draw(bakeProgram)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    /** The last bake's time on the GPU, once it's there, unless the GPU's clock jumped meanwhile (it can, the extension warns). */
    private fun readBakeTimer() {
        val value = IntArray(1)
        glGetQueryObjectuiv(bakeTimer, GL_QUERY_RESULT_AVAILABLE, value, 0)
        if (value[0] == 0) return
        timing = false
        glGetIntegerv(GL_GPU_DISJOINT_EXT, value, 0)
        if (value[0] != 0) return
        glGetQueryObjectuiv(bakeTimer, GL_QUERY_RESULT, value, 0)
        Trace.setCounter("bake GPU us", Integer.toUnsignedLong(value[0]) / 1000)
    }

    private fun draw(program: Int) {
        val position = glGetAttribLocation(program, "p")
        glEnableVertexAttribArray(position)
        glVertexAttribPointer(position, 2, GL_FLOAT, false, 0, triangle)
        glDrawArrays(GL_TRIANGLES, 0, 3)
    }

    private fun clampLinear(minFilter: Int) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    }

    private fun program(fragment: String): Int {
        val p = glCreateProgram()
        glAttachShader(p, shader(GL_VERTEX_SHADER, VERTEX))
        glAttachShader(p, shader(GL_FRAGMENT_SHADER, fragment))
        glLinkProgram(p)
        val ok = IntArray(1)
        glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { glGetProgramInfoLog(p) }
        return p
    }

    private fun shader(type: Int, source: String): Int {
        val s = glCreateShader(type)
        glShaderSource(s, source)
        glCompileShader(s)
        val ok = IntArray(1)
        glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { glGetShaderInfoLog(s) }
        return s
    }

    private companion object {
        const val FADE_MILLIS = 3000L

        // GL_EXT_disjoint_timer_query's, which GLES30 doesn't name.
        const val GL_TIME_ELAPSED_EXT = 0x88BF
        const val GL_GPU_DISJOINT_EXT = 0x8FBB

        // Barely changing: the zoom is there to leave room for the pan, which is the move you notice.
        const val MIN_ZOOM = 1.27f
        const val MAX_ZOOM = 1.33f

        // As much larger than 1080p as the furthest zoom, so there's still a texel for every screen pixel.
        const val BAKE_WIDTH = 2688
        const val BAKE_HEIGHT = 1512

        const val VERTEX = """#version 300 es
in vec2 p;
void main() { gl_Position = vec4(p, 0.0, 1.0); }
"""

        const val SHOW = """#version 300 es
precision highp float;
uniform sampler2D uTex;
uniform vec2 uView, uPan;
uniform float uZoom, uVig, uBright, uAlpha;
out vec4 o;
void main() {
    vec2 s = gl_FragCoord.xy / uView;
    vec3 c = texture(uTex, 0.5 + (s - 0.5) / uZoom + uPan).rgb;
    vec2 q = s - 0.5;
    c *= 1.0 - uVig * 0.85 * smoothstep(0.3, 0.95, length(q * vec2(1.0, 0.8)) * 1.45);
    o = vec4(c * uBright, uAlpha);
}
"""

        // Grain is a sum of three octaves of smoothed Gaussian noise, applied to brightness as a ratio so the
        // colour stays put, with an amplitude that peaks in the mid-tones (the AV1 film grain idea).
        const val BAKE = """#version 300 es
precision highp float;
uniform sampler2D uImg;
uniform vec2 uSize;
uniform float uRad, uAmt, uSoft, uHal, uWarm;
out vec4 o;
uint h(uint v) { uint s = v * 747796405u + 2891336453u; uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u; return (w >> 22u) ^ w; }
float r01(inout uint s) { s = h(s); return float(s) * (1.0 / 4294967296.0); }
float hn(vec2 c, uint k) {
    uint s = h(uint(int(c.x) + 100000) * 73856093u ^ uint(int(c.y) + 100000) * 19349663u ^ k * 83492791u);
    float a = r01(s), b = r01(s);
    return sqrt(-2.0 * log(max(a, 1e-6))) * cos(6.2832 * b);
}
float vn(vec2 p, uint k) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hn(i, k), hn(i + vec2(1, 0), k), f.x), mix(hn(i + vec2(0, 1), k), hn(i + vec2(1, 1), k), f.x), f.y);
}
float grain(vec2 gp) {
    return (vn(gp / (0.9 * uRad), 1u) * 0.5 + vn(gp / (1.8 * uRad), 2u) * 0.33 + vn(gp / (3.6 * uRad), 3u) * 0.17) * 1.6;
}
vec3 img(vec2 uv) { return texture(uImg, uv).rgb; }
void main() {
    vec2 uv = vec2(gl_FragCoord.x / uSize.x, 1.0 - gl_FragCoord.y / uSize.y);
    vec2 px = 1.0 / uSize;
    float sr = 0.5 + uSoft * 1.6;
    vec3 sm = img(uv) * 0.4 + (img(uv + vec2(sr, 0) * px) + img(uv - vec2(sr, 0) * px) + img(uv + vec2(0, sr) * px) + img(uv - vec2(0, sr) * px)) * 0.15;
    vec3 hb = vec3(0.0);
    for (int k = 0; k < 12; k++) {
        float a = float(k) * 0.5236;
        for (int q = 1; q <= 2; q++) hb += max(img(uv + vec2(cos(a), sin(a)) * float(q) * 6.0 * px) - 0.7, 0.0);
    }
    vec3 col = sm + vec3(1.0, 0.3, 0.1) * dot(hb / 24.0, vec3(0.3, 0.6, 0.1)) * uHal * 3.5;
    if (uAmt > 0.0) {
        float L = max(dot(col, vec3(0.3, 0.6, 0.1)), 0.003);
        float a = uAmt * 0.16 * sqrt(clamp(L * (1.0 - L), 0.0, 0.25)) * 2.0 * mix(1.0, 0.6, L);
        vec2 gp = gl_FragCoord.xy;
        float g = (grain(gp + vec2(-0.25)) + grain(gp + vec2(0.25, -0.25)) + grain(gp + vec2(-0.25, 0.25)) + grain(gp + vec2(0.25))) * 0.25;
        col *= max(L + a * g, 0.0) / L;
    }
    col *= vec3(1.0 + uWarm * 0.07, 1.0 + uWarm * 0.01, 1.0 - uWarm * 0.09);
    o = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
    }
}
