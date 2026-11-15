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
 * draws the texture as two quads — one per DS screen — a stripped-down
 * version of upstream's DSRenderer without backgrounds or user layouts:
 * the top 256x192 maps onto the top 4:3 window, the bottom 256x192 onto
 * the bottom 4:3 window, with a real hinge gap between them that is never
 * mapped to texture content, so each screen shows every pixel. [fitMode]
 * decides how each half fits its window (aspect-preserving FIT by
 * default, or FILL to stretch to the window).
 *
 * UV split follows upstream exactly: with the texture vertically flipped, the
 * top screen occupies v in [0, 0.5 - line] and the bottom screen v in
 * [0.5 + line, 1], where line = 1 / (384 + 1).
 *
 * Presentation-side smoothing filter: the black lines the user still sees on
 * the top (3D) screen are the game's own texture dark edge rows, rendered at
 * 3x into the compositor output and then shown at ~1:1 — a small vertical
 * low-pass in the presentation shader spreads each dark row over its
 * neighbours and drops its contrast ~20-30%. It is a 5-tap vertical blur
 * (±1 and ±2 texels, weights 0.4/0.2/0.1) applied to the top screen only
 * (v < 0.5); the bottom screen is 2D UI/text and is left unfiltered so its
 * text stays crisp. The texel size comes from [internalResolutionScale],
 * which the activity sets to the effective compositor scale (3 on the
 * OpenGL path, 1 on the software path — the compositor output is
 * 256x386 times the scale). Note Android's GLES bindings expose no
 * glGetTexLevelParameteriv, so the size cannot be queried from the texture
 * at runtime and must be passed in. The compositor texture is
 * CLAMP_TO_EDGE, so samples past the outer edges clamp safely, and the
 * separator rows between the screens are black-filled — the worst case is a
 * slightly darkened 1-2 rows at the top screen's bottom edge, at the hinge,
 * where the current GL_LINEAR sampling already blends the same way.
 *
 * 3x framebuffer check (done before adding the filter): the compositor
 * allocates ScreenW = 256*scale, ScreenH = (384+2)*scale, i.e. 768x1158 at
 * scale 3, with the 2*scale separator rows zero-filled at rows 192*scale —
 * the Kotlin UV split (line = 1/385) matches that separator exactly, so the
 * split is scale-invariant and there is no white-screen risk from the 3x
 * texture path. The one white screen the user saw on #54 was the game still
 * loading on entry, confirmed by him; 3x stays untouched.
 */
class MelonDSRenderer(
    private val onGlContextReady: (glContext: Long) -> Unit,
) : GLSurfaceView.Renderer {
    private data class FrameEvent(val glFenceSync: Long, val textureId: Int)

    private val frameEventQueue = LinkedList<FrameEvent>()

    private var program = 0
    private var uniformTex = -1
    private var uniformSwap = -1
    private var uniformTexelSize = -1
    private var uniformSmooth = -1

    /**
     * Effective compositor scale for the frame texture: 3 on the OpenGL path,
     * 1 on the software path (see RendererConfiguration.resolutionScaling).
     * The activity sets this before the first frame; the presentation filter
     * derives the texel size from it (texture is 256x386 times the scale).
     */
    @Volatile
    var internalResolutionScale: Int = 3

    /**
     * Whether the presentation smoothing filter runs (Settings → Advanced
     * → "NDS 平滑滤镜", default on). Read every frame from the uniform, so
     * flipping it takes effect on the next presented frame with no
     * recompile; the activity reads the preference once at session start,
     * so a change applies on the next game launch.
     */
    @Volatile
    var smoothingFilterEnabled: Boolean = true

    /**
     * How the game picture fits the shell's dual-screen window (smart
     * adaptation resolves it per game at launch; see [ScreenFitMode]).
     *
     * FIT keeps the core frame's aspect (no distortion; black bars where
     * the window's 2:3 aspect does not match the 256x384 frame). FILL
     * stretches the frame to the whole window (may distort slightly).
     * Changing it recomputes the screen rects from the last known surface
     * size, so the next presented frame uses the new mapping — no restart
     * needed. The bottom-screen touch transform follows automatically:
     * touch coordinates are normalized through [bottomScreenRect], which
     * is derived from the same rects.
     *
     * Per-system note: this is the NDS dual-screen fit. Other systems fit
     * their own screens (GBA's libretro single screen is already fitted to
     * the 3:2 GBA screen and never touches this; a future Switch path
     * resolves its own 16:9 fit) — never cross-apply one system's layout
     * to another's games.
     */
    @Volatile
    var fitMode: ScreenFitMode = ScreenFitMode.FIT
        set(value) {
            field = value
            if (lastSurfaceWidth > 0 && lastSurfaceHeight > 0) {
                updateScreenCoordinates(lastSurfaceWidth, lastSurfaceHeight)
            }
        }

    private var lastSurfaceWidth = 0
    private var lastSurfaceHeight = 0

    /**
     * Whether the frame texture's red and blue channels must be swapped back.
     *
     * The core's OpenGL compositor writes its output texture in BGRA order
     * on purpose (its final shader emits `pixel.bgr`; the desktop frontend
     * presents it as BGRA). Sampling that texture as plain RGBA swaps red
     * and blue across the whole picture — blue water turns orange, a red cap
     * turns blue, greens barely change, which is exactly what the #48/#50
     * device tests showed. The software renderer's texture is true RGBA and
     * must NOT be swapped, so the activity sets this from the configured
     * renderer before the emulator starts.
     */
    @Volatile
    var swapRedBlue: Boolean = false

    private var posBuffer: FloatBuffer? = null
    private var uvBuffer: FloatBuffer? = null

    /** Bottom DS screen area in surface (view) pixels, origin at the top-left. */
    @Volatile
    var bottomScreenRect: Rect = Rect()
        private set

    /**
     * Frames actually presented by this renderer in the last second.
     *
     * This is deliberately NOT the engine's own FPS counter: the native
     * getFPS() counts emulator-thread loop iterations, i.e. frames produced,
     * including frames this renderer then discards as stale — it read a
     * steady 60 while the user still felt outdoor stutter. Presented frames
     * are what the eye sees, so this is the number the overlay shows and the
     * one any auto-tuning must be driven by.
     */
    @Volatile
    var presentedFps: Int = 0
        private set

    private var presentedFrameCount = 0
    private var presentedWindowStartMs = 0L

    fun prepareNextFrame(glFenceSync: Long, textureId: Int) {
        synchronized(frameEventQueue) {
            frameEventQueue.add(FrameEvent(glFenceSync, textureId))
        }
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        program = createProgram()
        uniformTex = GLES30.glGetUniformLocation(program, "uTex")
        uniformSwap = GLES30.glGetUniformLocation(program, "uSwap")
        uniformTexelSize = GLES30.glGetUniformLocation(program, "uTexelSize")
        uniformSmooth = GLES30.glGetUniformLocation(program, "uSmooth")
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
        GLES30.glUniform1i(uniformSwap, if (swapRedBlue) 1 else 0)
        GLES30.glUniform1i(uniformSmooth, if (smoothingFilterEnabled) 1 else 0)
        GLES30.glUniform2f(
            uniformTexelSize,
            1f / (SCREEN_WIDTH * internalResolutionScale),
            1f / ((SCREEN_HEIGHT + SEPARATOR_ROWS) * internalResolutionScale),
        )

        positions.position(0)
        uvs.position(0)
        GLES30.glEnableVertexAttribArray(ATTRIB_POS)
        GLES30.glEnableVertexAttribArray(ATTRIB_UV)
        GLES30.glVertexAttribPointer(ATTRIB_POS, 2, GLES30.GL_FLOAT, false, 0, positions)
        GLES30.glVertexAttribPointer(ATTRIB_UV, 2, GLES30.GL_FLOAT, false, 0, uvs)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, positions.capacity() / 2)
        GLES30.glDisableVertexAttribArray(ATTRIB_POS)
        GLES30.glDisableVertexAttribArray(ATTRIB_UV)

        presentedFrameCount++
        val nowMs = System.currentTimeMillis()
        if (presentedWindowStartMs == 0L) {
            presentedWindowStartMs = nowMs
        } else if (nowMs - presentedWindowStartMs >= 1000L) {
            presentedFps = (presentedFrameCount * 1000L / (nowMs - presentedWindowStartMs)).toInt()
            presentedFrameCount = 0
            presentedWindowStartMs = nowMs
        }
    }

    private fun updateScreenCoordinates(surfaceWidth: Int, surfaceHeight: Int) {
        lastSurfaceWidth = surfaceWidth
        lastSurfaceHeight = surfaceHeight
        // Two genuinely separate windows, one per DS screen — not one
        // 256x384 block with a line drawn across it. The surface is
        // sized by the shell geometry as (windowWidth x (2 x windowHeight
        // + hingeGap)), each window exactly 4:3; the top 256x192 of the
        // core texture maps onto the top window, the bottom 256x192 onto
        // the bottom window, and the hinge gap between them is left
        // undrawn (the surface's clear color — black — shows there, and
        // the shell's opaque hinge barrel covers the middle of it).
        //
        // Hard rule: no pixel is ever lost. Each window shows its full
        // 256x192 — the gap is never mapped to texture content, so
        // nothing is cropped, squashed or stretched to hide under a
        // cover (the old overlay band ate ~3 game pixels per side of the
        // seam; a "stretch the whole block, then cover the gap" mapping
        // would hide whole rows the same way). [ScreenFitMode] applies
        // per window: FIT keeps the 4:3 aspect (bars inside a window
        // when the surface is wider than the window); FILL stretches to
        // the window. On NDS the windows are 4:3 and the texture halves
        // are 4:3, so FIT and FILL coincide here — the framework's value
        // is the per-system structure + the seed mechanism, not a visible
        // difference on this system.
        //
        // The UV split is unchanged — the whole texture is still sampled
        // (top v in [0, 0.5 - line], bottom v in [0.5 + line, 1], the
        // 2 separator rows skipped) — only the quad geometry changes,
        // and bottomScreenRect (which the touch transform normalizes
        // through) is derived from the same rects, so touch stays aligned.
        val windowHeight = surfaceWidth * 3f / 4f
        var hingeGapPx = surfaceHeight - windowHeight * 2f
        var windowHeightPx = windowHeight
        if (hingeGapPx < 0f) {
            // Defensive: the geometry guarantees a real gap, but a
            // degenerate surface must not invert the windows — fall back
            // to two adjacent windows with no gap rather than crashing.
            hingeGapPx = 0f
            windowHeightPx = surfaceHeight / 2f
        }
        val (quadWidth, quadHeight) =
            when (fitMode) {
                ScreenFitMode.FIT -> {
                    val scale =
                        minOf(
                            surfaceWidth / SCREEN_WIDTH.toFloat(),
                            windowHeightPx / (SCREEN_HEIGHT / 2).toFloat(),
                        )
                    SCREEN_WIDTH * scale to (SCREEN_HEIGHT / 2) * scale
                }
                ScreenFitMode.FILL -> surfaceWidth.toFloat() to windowHeightPx
            }
        val originX = (surfaceWidth - quadWidth) / 2f
        val topOriginY = (windowHeightPx - quadHeight) / 2f
        val bottomOriginY = windowHeightPx + hingeGapPx + (windowHeightPx - quadHeight) / 2f

        val topRect = RectF(originX, topOriginY, originX + quadWidth, topOriginY + quadHeight)
        val bottomRect = RectF(originX, bottomOriginY, originX + quadWidth, bottomOriginY + quadHeight)
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

        /** Black-filled separator rows between the two screens in the texture. */
        private const val SEPARATOR_ROWS = 2
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
                "uniform int uSwap;\n" +
                "uniform int uSmooth;\n" +
                "uniform vec2 uTexelSize;\n" +
                "in vec2 vUv;\n" +
                "out vec4 oColor;\n" +
                "void main() {\n" +
                "    vec4 c = texture(uTex, vUv);\n" +
                "    if (uSmooth == 1 && vUv.y < 0.5) {\n" +
                "        float dy = uTexelSize.y;\n" +
                "        vec4 cUp1 = texture(uTex, vUv + vec2(0.0, dy));\n" +
                "        vec4 cDn1 = texture(uTex, vUv - vec2(0.0, dy));\n" +
                "        vec4 cUp2 = texture(uTex, vUv + vec2(0.0, dy * 2.0));\n" +
                "        vec4 cDn2 = texture(uTex, vUv - vec2(0.0, dy * 2.0));\n" +
                "        c = c * 0.4 + (cUp1 + cDn1) * 0.2 + (cUp2 + cDn2) * 0.1;\n" +
                "    }\n" +
                "    oColor = (uSwap == 1) ? c.bgra : c;\n" +
                "}\n"
    }
}
