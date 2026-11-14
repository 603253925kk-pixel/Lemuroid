package com.swordfish.lemuroid.app.shared.game.melonds

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.game.GameService
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.magnum.melonds.MelonDSAndroidInterface
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.common.RetroAchievementsCallback
import me.magnum.melonds.common.UriFileHandler
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.ui.emulator.EmulatorFrameRenderedListener
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Thin play layer for DS games on the vendored melonDS engine (route (b) of
 * the vendor plan): it wires the engine's JNI bridge directly — GL surface,
 * touch screen, buttons, save bridging and play time — instead of porting the
 * upstream app layer (EmulatorActivity/ViewModel and its twelve repository
 * collaborators).
 *
 * Deliberately absent for now: savestate UI (engine states are not compatible
 * with the libretro ones, so no half-wired entry point is shown), the shell's
 * Compose game menu and the DS handheld shell (later milestones).
 */
class MelonDSGameActivity : ImmersiveActivity() {
    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var directoriesManager: DirectoriesManager

    private lateinit var game: Game
    private lateinit var saveBridge: MelonDSSaveBridge
    private lateinit var playTimeTracker: MelonDSPlayTimeTracker

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var renderer: MelonDSRenderer
    private lateinit var fpsLabel: TextView

    private val screenshotBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(SCREEN_WIDTH * SCREEN_HEIGHT * 4).order(ByteOrder.nativeOrder())

    @Volatile
    private var loadStarted = false

    @Volatile
    private var emulationStarted = false

    @Volatile
    private var emulationPaused = false

    @Volatile
    private var lifecyclePaused = false

    private var touchscreenHeld = false
    private var fastForwardEnabled = false

    @Volatile
    private var playTimeTrackingActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GameService.startService(applicationContext, intent)
        game = intent.getSerializableExtra(EXTRA_GAME) as Game
        saveBridge = MelonDSSaveBridge(directoriesManager.getSavesDirectory())
        playTimeTracker = MelonDSPlayTimeTracker(game, retrogradeDb, lifecycleScope)

        renderer = MelonDSRenderer(onGlContextReady = { glContext -> onGlContextReady(glContext) })
        // The OpenGL compositor's output texture is BGRA-ordered by design;
        // the software renderer's is plain RGBA. Swap channels back only on
        // the OpenGL path (see MelonDSRenderer.swapRedBlue).
        renderer.swapRedBlue =
            !SharedPreferencesHelper
                .getSharedPreferences(applicationContext)
                .getBoolean(getString(R.string.pref_key_melonds_software_renderer), false)
        surfaceView =
            GLSurfaceView(this).apply {
                setEGLContextClientVersion(3)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                preserveEGLContextOnPause = true
                setOnTouchListener { _, event -> handleSurfaceTouch(event) }
            }

        // Layout (M4): the v3.3 handheld shell hosts the surface and the
        // controls deck — the GLSurfaceView goes inside GameViewportShellBox
        // (sized exactly to the 2:3 viewport by the shell geometry) and the
        // existing View deck sits on the shell's lower body. The previous
        // plain LinearLayout version (surface box + bare deck on black) is
        // what the user rejected on sight in the #48 test. The activity is
        // portrait-locked (manifest), so the shell never has to survive a
        // rotation-driven surface re-creation.
        setContentView(
            androidx.compose.ui.platform.ComposeView(this).apply {
                setContent {
                    MelonDSGameShellContent(
                        surfaceView = surfaceView,
                        controlsDeck = buildControlsDeck(),
                    )
                }
            },
        )

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = exitGame()
            },
        )
    }

    // ------------------------------------------------------------------ setup

    private fun onGlContextReady(glContext: Long) {
        if (loadStarted) return
        loadStarted = true
        lifecycleScope.launch {
            var stage = "ensureSetup"
            val loadResult =
                runCatching {
                    withContext(Dispatchers.IO) {
                        MelonDSEngine.ensureSetup(applicationContext)
                        stage = "setupEmulator"
                        MelonEmulator.setupEmulator(
                            MelonDSEmulatorConfig.build(
                                applicationContext,
                                useSoftwareRenderer =
                                    SharedPreferencesHelper
                                        .getSharedPreferences(applicationContext)
                                        .getBoolean(getString(R.string.pref_key_melonds_software_renderer), false),
                            ),
                            assets,
                            null,
                            NoOpRetroAchievementsCallback,
                            EmulatorFrameRenderedListener { glFenceSync, textureId ->
                                renderer.prepareNextFrame(glFenceSync, textureId)
                                surfaceView.requestRender()
                            },
                            screenshotBuffer,
                            glContext,
                        )
                        stage = "loadRom"
                        val sramFile = saveBridge.migrateToEngine(game)
                        MelonEmulator.loadRom(
                            Uri.parse(game.fileUri),
                            Uri.fromFile(sramFile),
                            MelonEmulator.GbaSlotType.NONE,
                            null,
                            null,
                        )
                    }
                }
            loadResult.fold(
                onSuccess = { result ->
                    if (result.isTerminal) {
                        Timber.e("melonDS engine failed to load ROM: $result")
                        failAndExit("loadRom: $result")
                    } else {
                        MelonEmulator.startEmulation()
                        emulationStarted = true
                        if (lifecyclePaused) {
                            MelonEmulator.pauseEmulation()
                            emulationPaused = true
                        }
                        playTimeTracker.start()
                        playTimeTrackingActive = true
                        startFpsUpdates()
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "melonDS engine failed to start")
                    failAndExit("$stage: ${error.javaClass.simpleName}: ${error.message}")
                },
            )
        }
    }

    private fun failAndExit(detail: String? = null) {
        // Show the real failure reason: the generic message alone hid for
        // two builds whether the native libraries, the emulator setup or
        // the ROM load was at fault (Build APK #44/#45 both failed blind).
        val message =
            if (detail.isNullOrBlank()) {
                "新引擎加载失败，请在设置中关闭「NDS 使用 melonDS 新引擎」后重试"
            } else {
                "新引擎加载失败（$detail），请在设置中关闭「NDS 使用 melonDS 新引擎」后重试"
            }
        displayToast(message, android.widget.Toast.LENGTH_LONG)
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun exitGame() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    // -------------------------------------------------------------- lifecycle

    override fun onPause() {
        super.onPause()
        lifecyclePaused = true
        if (emulationStarted && !emulationPaused) {
            MelonEmulator.pauseEmulation()
            emulationPaused = true
        }
        surfaceView.onPause()
        lifecycleScope.launch {
            runCatching { playTimeTracker.flush() }
            withContext(Dispatchers.IO) { runCatching { saveBridge.syncBackToHost(game) } }
        }
    }

    override fun onResume() {
        super.onResume()
        surfaceView.onResume()
        lifecyclePaused = false
        if (emulationStarted && emulationPaused) {
            MelonEmulator.resumeEmulation()
            emulationPaused = false
        }
    }

    override fun onDestroy() {
        if (emulationStarted) {
            runCatching { MelonEmulator.stopEmulation() }
            emulationStarted = false
        }
        // Best-effort synchronous final flush: the process may die right after.
        runCatching { saveBridge.syncBackToHost(game) }
        if (::playTimeTracker.isInitialized && playTimeTrackingActive) {
            runCatching { runBlocking { playTimeTracker.flush() } }
        }
        // Mirror BaseGameActivity: without this the :game process (and the
        // file lock it holds) outlives the session, and GameLauncher then
        // refuses every later launch with "close the current game" forever.
        if (!isChangingConfigurations) {
            GameService.requestTermination()
        }
        super.onDestroy()
    }

    // ----------------------------------------------------------------- input

    private fun handleSurfaceTouch(event: MotionEvent): Boolean {
        val rect = renderer.bottomScreenRect
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (rect.isEmpty || !rect.contains(event.x.toInt(), event.y.toInt())) return false
                touchscreenHeld = true
                sendTouch(event, rect)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchscreenHeld) return false
                sendTouch(event, rect)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!touchscreenHeld) return false
                touchscreenHeld = false
                MelonEmulator.onScreenRelease()
                return true
            }
            else -> return touchscreenHeld
        }
    }

    private fun sendTouch(event: MotionEvent, rect: android.graphics.Rect) {
        val dsX = ((event.x - rect.left) * (DS_SCREEN_WIDTH - 1) / rect.width()).toInt().coerceIn(0, DS_SCREEN_WIDTH - 1)
        val dsY = ((event.y - rect.top) * (DS_SCREEN_HEIGHT - 1) / rect.height()).toInt().coerceIn(0, DS_SCREEN_HEIGHT - 1)
        MelonEmulator.onScreenTouch(dsX, dsY)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val input =
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> Input.A
                KeyEvent.KEYCODE_BUTTON_B -> Input.B
                KeyEvent.KEYCODE_BUTTON_X -> Input.X
                KeyEvent.KEYCODE_BUTTON_Y -> Input.Y
                KeyEvent.KEYCODE_BUTTON_L1 -> Input.L
                KeyEvent.KEYCODE_BUTTON_R1 -> Input.R
                KeyEvent.KEYCODE_BUTTON_START -> Input.START
                KeyEvent.KEYCODE_BUTTON_SELECT -> Input.SELECT
                KeyEvent.KEYCODE_DPAD_UP -> Input.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> Input.DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> Input.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> Input.RIGHT
                else -> null
            } ?: return super.dispatchKeyEvent(event)

        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) MelonEmulator.onInputDown(input)
            KeyEvent.ACTION_UP -> MelonEmulator.onInputUp(input)
        }
        return true
    }

    // -------------------------------------------------------------------- UI

    private fun startFpsUpdates() {
        lifecycleScope.launch {
            while (emulationStarted) {
                // Presented frames first (what the eye actually gets); the
                // engine counter second, because it counts produced frames
                // the renderer may have discarded and read 60 during visible
                // outdoor stutter in the #48 test.
                fpsLabel.text = "显示 ${renderer.presentedFps} · 引擎 ${MelonEmulator.getFPS()} FPS"
                delay(1000)
            }
        }
    }

    /**
     * The controls deck below the screens: L/R and the FPS readout on the top
     * row, D-pad and ABXY centered vertically on the left/right, and the
     * SELECT/START/fast-forward/exit cluster along the bottom. Nothing in
     * here overlaps the game screens.
     */
    private fun buildControlsDeck(): View {
        val overlay = FrameLayout(this).apply { isClickable = false }

        fpsLabel =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = roundedBackground(alpha = 90)
            }
        overlay.addView(
            fpsLabel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                // Clears the shell body's POWER row (top ~19dp of the deck)
                // so the pill never prints over it.
            ).apply { topMargin = dp(34) },
        )

        // L / R shoulders
        overlay.addView(
            padButton("L", Input.L, 64, 40),
            FrameLayout.LayoutParams(dp(64), dp(40), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(10)
                leftMargin = dp(12)
            },
        )
        overlay.addView(
            padButton("R", Input.R, 64, 40),
            FrameLayout.LayoutParams(dp(64), dp(40), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(10)
                rightMargin = dp(12)
            },
        )

        // D-pad (bottom-left cross)
        val dpad = FrameLayout(this)
        dpad.addView(padButton("▲", Input.UP, 56, 56), FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        dpad.addView(
            padButton("▼", Input.DOWN, 56, 56),
            FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
        )
        dpad.addView(padButton("◀", Input.LEFT, 56, 56), FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER_VERTICAL or Gravity.START))
        dpad.addView(padButton("▶", Input.RIGHT, 56, 56), FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER_VERTICAL or Gravity.END))
        overlay.addView(
            dpad,
            FrameLayout.LayoutParams(dp(168), dp(168), Gravity.CENTER_VERTICAL or Gravity.START).apply {
                leftMargin = dp(16)
            },
        )

        // ABXY diamond (bottom-right)
        val abxy = FrameLayout(this)
        abxy.addView(padButton("X", Input.X, 56, 56), FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        abxy.addView(padButton("B", Input.B, 56, 56), FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        abxy.addView(padButton("Y", Input.Y, 56, 56), FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER_VERTICAL or Gravity.START))
        abxy.addView(padButton("A", Input.A, 56, 56), FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER_VERTICAL or Gravity.END))
        overlay.addView(
            abxy,
            FrameLayout.LayoutParams(dp(168), dp(168), Gravity.CENTER_VERTICAL or Gravity.END).apply {
                rightMargin = dp(16)
            },
        )

        // Center cluster: select / start / fast-forward / exit
        val center =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
        center.addView(smallButton("SELECT") { bindHold(it, Input.SELECT) })
        center.addView(smallButton("START") { bindHold(it, Input.START) })
        center.addView(
            smallButton("»»") { view ->
                view.setOnClickListener {
                    fastForwardEnabled = !fastForwardEnabled
                    MelonEmulator.setFastForwardEnabled(fastForwardEnabled)
                    (view as TextView).alpha = if (fastForwardEnabled) 1.0f else 0.75f
                }
            },
        )
        center.addView(
            smallButton("✕") { view ->
                view.setOnClickListener { exitGame() }
            },
        )
        overlay.addView(
            center,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = dp(6) },
        )

        return overlay
    }

    private fun padButton(label: String, input: Input, widthDp: Int, heightDp: Int): TextView {
        return TextView(this).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = 0.75f
            background = roundedBackground(alpha = 110)
            bindHold(this, input)
        }
    }

    private fun smallButton(label: String, bind: (View) -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = 0.75f
            background = roundedBackground(alpha = 90)
            layoutParams =
                LinearLayout.LayoutParams(dp(56), dp(34)).apply {
                    leftMargin = dp(4)
                    rightMargin = dp(4)
                }
            bind(this)
        }
    }

    private fun bindHold(view: View, input: Input) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    MelonEmulator.onInputDown(input)
                    v.alpha = 1.0f
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    MelonEmulator.onInputUp(input)
                    v.alpha = 0.75f
                    true
                }
                else -> true
            }
        }
    }

    private fun roundedBackground(alpha: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.argb(alpha, 40, 40, 40))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private object NoOpRetroAchievementsCallback : RetroAchievementsCallback {
        override fun onAchievementPrimed(achievementId: Long) = Unit

        override fun onAchievementTriggered(achievementId: Long) = Unit

        override fun onAchievementUnprimed(achievementId: Long) = Unit
    }

    companion object {
        private const val EXTRA_GAME = "GAME"
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 384
        private const val DS_SCREEN_WIDTH = 256
        private const val DS_SCREEN_HEIGHT = 192

        fun launchGame(activity: Activity, game: Game) {
            activity.startActivityForResult(
                Intent(activity, MelonDSGameActivity::class.java).apply {
                    putExtra(EXTRA_GAME, game)
                },
                BaseGameActivity.REQUEST_PLAY_GAME,
            )
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}

/** One-time native interface setup for the vendored engine (per process). */
private object MelonDSEngine {
    private val setupDone = AtomicBoolean(false)

    fun ensureSetup(context: Context) {
        if (setupDone.compareAndSet(false, true)) {
            try {
                val appContext = context.applicationContext
                MelonDSAndroidInterface.setup(UriFileHandler(appContext, MelonDSUriHandler(appContext)))
            } catch (error: Throwable) {
                setupDone.set(false)
                throw error
            }
        }
    }
}
