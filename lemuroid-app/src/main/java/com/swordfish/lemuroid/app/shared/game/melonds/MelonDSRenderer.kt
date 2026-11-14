package com.swordfish.lemuroid.app.shared.game.melonds

import android.graphics.Rect
import android.opengl.EGL14
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.LinkedList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Minimal renderer for the vendored melonDS engine.
 *
 * The core renders both DS screens into a single 256x384 GL texture (the two
 * 256x192 screens stacked, with a 2px separator line) and reports every frame
 * through the emulator's frame-rendered listener as a (GL fence sync, texture
 * id) pair. This renderer keeps only the newest event, waits on its fence and
 * draws the texture as two quads — a stripped-down version of upstream's
 * DSRenderer without backgrounds or user layouts: the stacked screens are
 * fitted into the surface preserving aspect, centered.
 *
 * UV split follows upstream exactly: with the texture vertically flipped, the
 * top screen occupies v in [0, 0.5 - line] and the bottom screen v in
 * [0.5 + line, 1], where line = 1 / (384 + 1).
 */
class MelonDSRenderer(
    private val onGlContextReady: (glContext: Long) -> Unit,
) : GLSurfaceView.Renderer {
    private data class FrameEvent(val glFenceSync: Long, val textureId: Int)

    private val frameEventQueue = LinkedList<FrameEvent>()

    private var program = 0
    private var uniformTex = -1

    private var posBuffer: FloatBuffer? = null
    private var uvBuffer: FloatBuffer? = null

    /** Bottom DS screen area in surface (view) pixels, origin at the top-left. */
    @Volatile
    var bottomScreenRect: Rect = Rect()
        private set

    fun prepareNextFrame(glFenceSync: Long, textureId: Int) {
        synchronized(frameEventQueue) {
            frameEventQueue.add(FrameEvent(glFenceSync, textureId))
        }
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        program = createProgram()
        uniformTex = GLES30.glGetUniformLocation(program, "uTex")
        onGlContextReady(EGL14.eglGetCurrentContext().nativeHandle)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        updateScreenCoordinates(width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        val event =
            synchronized(frameEventQueue) {
                val newest = frameEventQueue.pollLast() ?: return
                // Discard stale frames, releasing their fences.
                while (frameEventQueue.isNotEmpty()) {
                    GLES30.glDeleteSync(frameEventQueue.poll().glFenceSync)
                }
                newest
            }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val positions = posBuffer ?: return
        val uvs = uvBuffer ?: return

        GLES30.glUseProgram(program)
        GLES30.glWaitSync(event.glFenceSync, 0, GLES30.GL_TIMEOUT_IGNORED)
        GLES30.glDeleteSync(event.glFenceSync)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, event.textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glUniform1i(uniformTex, 0)

        positions.position(0)
        uvs.position(0)
        GLES30.glEnableVertexAttribArray(ATTRIB_POS)
        GLES30.glEnableVertexAttribArray(ATTRIB_UV)
        GLES30.glVertexAttribPointer(ATTRIB_POS, 2, GLES30.GL_FLOAT, false, 0, positions)
        GLES30.glVertexAttribPointer(ATTRIB_UV, 2, GLES30.GL_FLOAT, false, 0, uvs)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, positions.capacity() / 2)
        GLES30.glDisableVertexAttribArray(ATTRIB_POS)
        GLES30.glDisableVertexAttribArray(ATTRIB_UV)
    }

    private fun updateScreenCoordinates(surfaceWidth: Int, surfaceHeight: Int) {
        val scale = minOf(surfaceWidth / SCREEN_WIDTH.toFloat(), surfaceHeight / SCREEN_HEIGHT.toFloat())
        val contentWidth = SCREEN_WIDTH * scale
        val contentHeight = SCREEN_HEIGHT * scale
        val originX = (surfaceWidth - contentWidth) / 2f
        val originY = (surfaceHeight - contentHeight) / 2f
        val screenHeightPx = contentHeight / 2f

        val topRect = RectF(originX, originY, originX + contentWidth, originY + screenHeightPx)
        val bottomRect = RectF(originX, originY + screenHeightPx, originX + contentWidth, originY + contentHeight)
        bottomScreenRect =
            Rect(
                bottomRect.left.toInt(),
                bottomRect.top.toInt(),
                bottomRect.right.toInt(),
                bottomRect.bottom.toInt(),
            )

        val lineRelativeSize = 1f / (SCREEN_HEIGHT + 1)
        val positions = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()

        fun addQuad(rect: RectF, vTop: Float, vBottom: Float) {
            // Two triangles; texture is vertically flipped (v=0 is the top).
            val corners =
                listOf(
                    rect.left to rect.bottom,
                    rect.left to rect.top,
                    rect.right to rect.top,
                    rect.left to rect.bottom,
                    rect.right to rect.top,
                    rect.right to rect.bottom,
                )
            val uvCorners =
                listOf(
                    0f to vBottom,
                    0f to vTop,
                    1f to vTop,
                    0f to vBottom,
                    1f to vTop,
                    1f to vBottom,
                )
            corners.forEachIndexed { index, (x, y) ->
                positions.add((x / surfaceWidth) * 2f - 1f)
                positions.add(((surfaceHeight - y) / surfaceHeight) * 2f - 1f)
                uvs.add(uvCorners[index].first)
                uvs.add(uvCorners[index].second)
            }
        }

        addQuad(topRect, 0f, 0.5f - lineRelativeSize)
        addQuad(bottomRect, 0.5f + lineRelativeSize, 1f)

        posBuffer =
            ByteBuffer.allocateDirect(4 * positions.size)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(positions.toFloatArray())
        uvBuffer =
            ByteBuffer.allocateDirect(4 * uvs.size)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(uvs.toFloatArray())
    }

    private fun createProgram(): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        return shader
    }

    private data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

    companion object {
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 384
        private const val ATTRIB_POS = 0
        private const val ATTRIB_UV = 1

        private const val VERTEX_SHADER =
            "#version 300 es\n" +
                "layout(location = 0) in vec2 aPos;\n" +
                "layout(location = 1) in vec2 aUv;\n" +
                "out vec2 vUv;\n" +
                "void main() { vUv = aUv; gl_Position = vec4(aPos, 0.0, 1.0); }\n"

        private const val FRAGMENT_SHADER =
            "#version 300 es\n" +
                "precision mediump float;\n" +
                "uniform sampler2D uTex;\n" +
                "in vec2 vUv;\n" +
                "out vec4 oColor;\n" +
                "void main() { oColor = texture(uTex, vUv); }\n"
    }
}
