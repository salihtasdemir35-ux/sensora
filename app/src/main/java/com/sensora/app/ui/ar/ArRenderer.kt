package com.sensora.app.ui.ar

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.sensora.app.core.camera.HitKind
import com.sensora.app.core.camera.Vec3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

data class ArPoint(
    val world: Vec3,
    val screenX: Float,
    val screenY: Float,
    val onScreen: Boolean,
    val kind: HitKind,
    val placedAtDistance: Double,
)

data class ArUiState(
    val tracking: Boolean = false,
    val failure: TrackingFailureReason = TrackingFailureReason.NONE,
    val surfacesFound: Boolean = false,
    val points: List<ArPoint> = emptyList(),
    val centerDistance: Double? = null,
    val centerKind: HitKind? = null,
)

enum class ArAction { ADD_AT_CENTER, UNDO, CLEAR }

/** Draws the camera image with OpenGL ES 2 and keeps measuring anchors. Runs on the GL thread. */
class ArController(private val context: Context) : GLSurfaceView.Renderer {
    @Volatile var session: Session? = null
    private val _state = MutableStateFlow(ArUiState())
    val state: StateFlow<ArUiState> = _state.asStateFlow()

    private val actions = ConcurrentLinkedQueue<ArAction>()
    private val anchors = ArrayList<Triple<Anchor, HitKind, Double>>()
    private val background = BackgroundRenderer()
    private var textureSet = false
    private var width = 1
    private var height = 1
    private var geometryDirty = true
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProj = FloatArray(16)

    fun post(action: ArAction) { actions.add(action) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.create()
        textureSet = false
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        width = w; height = h; geometryDirty = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        if (!textureSet) { s.setCameraTextureName(background.textureId); textureSet = true }
        if (geometryDirty) {
            s.setDisplayGeometry(ContextCompat.getDisplayOrDefault(context).rotation, width, height)
            geometryDirty = false
        }
        val frame = try { s.update() } catch (e: Exception) { return }
        background.draw(frame)

        val camera = frame.camera
        val tracking = camera.trackingState == TrackingState.TRACKING
        val cx = width / 2f; val cy = height / 2f
        while (true) {
            when (actions.poll() ?: break) {
                ArAction.ADD_AT_CENTER -> if (tracking) bestHit(frame, cx, cy)?.let { (hit, kind) ->
                    anchors.add(Triple(hit.createAnchor(), kind, hit.distance.toDouble()))
                }
                ArAction.UNDO -> anchors.removeLastOrNull()?.first?.detach()
                ArAction.CLEAR -> { anchors.forEach { it.first.detach() }; anchors.clear() }
            }
        }

        val center = if (tracking) bestHit(frame, cx, cy) else null
        camera.getProjectionMatrix(proj, 0, 0.05f, 100f)
        camera.getViewMatrix(view, 0)
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)
        val points = anchors.filter { it.first.trackingState != TrackingState.STOPPED }.map { (anchor, kind, dist) ->
            val p = anchor.pose
            val scr = toScreen(p)
            ArPoint(Vec3(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble()),
                scr?.first ?: 0f, scr?.second ?: 0f, scr != null, kind, dist)
        }
        val surfaces = s.getAllTrackables(Plane::class.java).any { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
        _state.value = ArUiState(tracking, camera.trackingFailureReason, surfaces, points,
            center?.first?.distance?.toDouble(), center?.second)
    }

    private fun toScreen(p: Pose): Pair<Float, Float>? {
        val v = floatArrayOf(p.tx(), p.ty(), p.tz(), 1f)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, viewProj, 0, v, 0)
        if (out[3] <= 0f) return null
        return ((out[0] / out[3] + 1f) / 2f * width) to ((1f - out[1] / out[3]) / 2f * height)
    }

    /** Prefers detected planes, then depth points, then oriented feature points. */
    private fun bestHit(frame: Frame, x: Float, y: Float): Pair<HitResult, HitKind>? {
        for (hit in frame.hitTest(x, y)) {
            val t = hit.trackable
            when {
                t is Plane && t.trackingState == TrackingState.TRACKING && t.isPoseInPolygon(hit.hitPose) -> return hit to HitKind.PLANE
                t is DepthPoint -> return hit to HitKind.DEPTH
                t is Point && t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL -> return hit to HitKind.POINT
            }
        }
        return null
    }

    fun releaseAnchors() {
        anchors.forEach { runCatching { it.first.detach() } }
        anchors.clear()
    }
}

/** Renders the ARCore camera texture as a full-screen quad. */
class BackgroundRenderer {
    var textureId = -1; private set
    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private val quad: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val texCoords: FloatBuffer = floatBuffer(FloatArray(8))

    fun create() {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(target, textureId)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, VERTEX))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
        GLES20.glLinkProgram(program)
        aPosition = GLES20.glGetAttribLocation(program, "a_Position")
        aTexCoord = GLES20.glGetAttribLocation(program, "a_TexCoord")
    }

    fun draw(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            quad.position(0); texCoords.position(0)
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad, Coordinates2d.TEXTURE_NORMALIZED, texCoords)
        }
        if (frame.timestamp == 0L) return
        quad.position(0); texCoords.position(0)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUseProgram(program)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    private companion object {
        fun floatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }

        const val VERTEX = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() { gl_Position = a_Position; v_TexCoord = a_TexCoord; }
        """
        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() { gl_FragColor = texture2D(sTexture, v_TexCoord); }
        """
    }
}
