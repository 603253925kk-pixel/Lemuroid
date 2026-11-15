package com.swordfish.lemuroid.app.shared.game.melonds

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.game.GameService
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import gg.padkit.inputevents.InputEvent
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
 * Deliberately absent for now: the shell's Compose game menu (the ☰ deck
 * button opens the shared GameMenuActivity since the menu-restore batch)
 * and the DS handheld shell (later milestones).
 */
class MelonDSGameActivity : ImmersiveActivity() {
    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var directoriesManager: DirectoriesManager

    @Inject
    lateinit var melonDSStatesManager: MelonDSStatesManager

    private lateinit var game: Game
    private lateinit var systemCoreConfig: SystemCoreConfig
    private lateinit var saveBridge: MelonDSSaveBridge
    private lateinit var playTimeTracker: MelonDSPlayTimeTracker

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var renderer: MelonDSRenderer

    // Compose state for the shell: the FPS readout (updated once a second)
    // and the fast-forward toggle. Read inside setContent, so the shell
    // recomposes when they change.
    private val fpsTextState = mutableStateOf("")
    private val fastForwardState = mutableStateOf(false)

    // D-pad directions currently held from the PadKit deck, so direction
    // changes can be diffed into individual press/release calls.
    private val dpadPressed = mutableSetOf<Input>()

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

    @Volatile
    private var playTimeTrackingActive = false

    // Game-menu state: set while the shared GameMenuActivity is on top, so a
    // second ☰ press cannot stack another menu and the result handler knows
    // whether the session was running when the menu opened.
    private var menuOpen = false
    private var menuOpenedWhileRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GameService.startService(applicationContext, intent)
        game = intent.getSerializableExtra(EXTRA_GAME) as Game
        systemCoreConfig = intent.getSerializableExtra(EXTRA_SYSTEM_CORE_CONFIG) as SystemCoreConfig
        saveBridge = MelonDSSaveBridge(directoriesManager.getSavesDirectory())
        playTimeTracker = MelonDSPlayTimeTracker(game, retrogradeDb, lifecycleScope)

        renderer = MelonDSRenderer(onGlContextReady = { glContext -> onGlContextReady(glContext) })
        val useSoftwareRenderer =
            SharedPreferencesHelper
                .getSharedPreferences(applicationContext)
                .getBoolean(getString(R.string.pref_key_melonds_software_renderer), false)
        // The OpenGL compositor's output texture is BGRA-ordered by design;
        // the software renderer's is plain RGBA. Swap channels back only on
        // the OpenGL path (see MelonDSRenderer.swapRedBlue).
        renderer.swapRedBlue = !useSoftwareRenderer
        // The presentation filter needs the real texel size: the compositor
        // output is 256x386 times the effective scale, which is 3 on the
        // OpenGL path and 1 on the software path.
        renderer.internalResolutionScale =
            if (useSoftwareRenderer) 1 else MelonDSEmulatorConfig.INTERNAL_RESOLUTION_SCALING
        surfaceView =
            GLSurfaceView(this).apply {
                setEGLContextClientVersion(3)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                preserveEGLContextOnPause = true
                setOnTouchListener { _, event -> handleSurfaceTouch(event) }
            }

        // Layout (M4, deck redone in #54): the v3.3 handheld shell hosts the
        // surface and the v3.3 PadKit controls deck — the GLSurfaceView goes
        // inside GameViewportShellBox (sized exactly to the 2:3 viewport by
        // the shell geometry) and the MELONDS pads, fast-forward pill and FPS
        // readout live in the shell's lower body. The previous plain
        // LinearLayout version (surface box + bare deck on black) is what
        // the user rejected on sight in the #48 test, and the flat View
        // buttons that replaced it in M4 are what he rejected again after
        // #53. The activity is portrait-locked (manifest), so the shell
        // never has to survive a rotation-driven surface re-creation.
        setContentView(
            androidx.compose.ui.platform.ComposeView(this).apply {
                setContent {
                    MelonDSGameShellContent(
                        surfaceView = surfaceView,
                        fpsText = fpsTextState.value,
                        fastForwardEnabled = fastForwardState.value,
                        onToggleFastForward = {
                            fastForwardState.value = !fastForwardState.value
                            MelonEmulator.setFastForwardEnabled(fastForwardState.value)
                        },
                        onPadInputEvent = ::handlePadInputEvent,
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

    // -------------------------------------------------------------- game menu

    /**
     * Opens the shared system menu (GameMenuActivity), the same menu the
     * libretro path shows: play time, engine-native savestate slots, quit,
     * restart, fast-forward and the cheats entry (cheats themselves are
     * applied by the M3 adapter). The engine is paused while the menu is up.
     *
     * Entries the engine cannot serve are hidden by the request, not shown
     * dead: no runtime audio toggle (the engine has none), no in-game
     * touch-controls editor (it belongs to the libretro screen). The
     * save/load slots are the engine's own states in `states/melonds-engine/`
     * — never the libretro `states/melonds/` slots, which are a different
     * format and would corrupt the session if loaded here.
     */
    private fun openMenu() {
        if (menuOpen) return
        if (!emulationStarted) {
            displayToast("游戏加载中，请稍等")
            return
        }
        menuOpen = true
        menuOpenedWhileRunning = emulationStarted && !emulationPaused
        if (menuOpenedWhileRunning) {
            MelonEmulator.pauseEmulation()
            emulationPaused = true
        }
        lifecycleScope.launch {
            val totalPlayTime =
                withContext(Dispatchers.IO) {
                    retrogradeDb.gameDao().selectById(game.id)?.totalPlayTimeMillis
                        ?: game.totalPlayTimeMillis
                }
            val intent =
                Intent(this@MelonDSGameActivity, GameMenuActivity::class.java).apply {
                    putExtra(GameMenuContract.EXTRA_CORE_OPTIONS, emptyArray<LemuroidCoreOption>())
                    putExtra(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS, emptyArray<LemuroidCoreOption>())
                    putExtra(GameMenuContract.EXTRA_GAME, game)
                    putExtra(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
                    putExtra(GameMenuContract.EXTRA_AUDIO_ENABLED, true)
                    putExtra(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, true)
                    putExtra(GameMenuContract.EXTRA_FAST_FORWARD, fastForwardState.value)
                    putExtra(GameMenuContract.EXTRA_DISKS, 0)
                    putExtra(GameMenuContract.EXTRA_CURRENT_DISK, 0)
                    putExtra(GameMenuContract.EXTRA_TOTAL_PLAY_TIME_MILLIS, totalPlayTime)
                    putExtra(GameMenuContract.EXTRA_SESSION_PLAY_TIME_MILLIS, playTimeTracker.sessionPlayTimeMillis())
                    putExtra(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG, TiltConfiguration.Disabled)
                    putExtra(GameMenuContract.EXTRA_TILT_ALL_CONFIGS, emptyArray<TiltConfiguration>())
                    putExtra(GameMenuContract.EXTRA_ENGINE_STATES, true)
                    putExtra(GameMenuContract.EXTRA_AUDIO_TOGGLE_SUPPORTED, false)
                    putExtra(GameMenuContract.EXTRA_EDIT_TOUCH_CONTROLS_SUPPORTED, false)
                }
            startActivityForResult(intent, MENU_REQUEST)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != MENU_REQUEST) return
        menuOpen = false
        if (resultCode != Activity.RESULT_OK || data == null) {
            resumeAfterMenu()
            return
        }
        when {
            data.getBooleanExtra(GameMenuContract.RESULT_QUIT, false) -> {
                exitGame()
                return
            }
            data.getBooleanExtra(GameMenuContract.RESULT_RESET, false) -> {
                lifecycleScope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            runCatching { MelonEmulator.resetEmulation() }.getOrDefault(false)
                        }
                    if (!ok) displayToast("重启失败")
                    resumeAfterMenu()
                }
                return
            }
            data.hasExtra(GameMenuContract.RESULT_SAVE) -> {
                val slot = data.getIntExtra(GameMenuContract.RESULT_SAVE, 0)
                lifecycleScope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                MelonEmulator.saveState(Uri.fromFile(melonDSStatesManager.stateFile(game, slot)))
                            }.getOrDefault(false)
                        }
                    displayToast(if (ok) "即时存档已保存" else "即时存档保存失败")
                    resumeAfterMenu()
                }
                return
            }
            data.hasExtra(GameMenuContract.RESULT_LOAD) -> {
                val slot = data.getIntExtra(GameMenuContract.RESULT_LOAD, 0)
                lifecycleScope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                MelonEmulator.loadState(Uri.fromFile(melonDSStatesManager.stateFile(game, slot)))
                            }.getOrDefault(false)
                        }
                    displayToast(if (ok) "即时存档已载入" else "即时存档载入失败")
                    resumeAfterMenu()
                }
                return
            }
            data.hasExtra(GameMenuContract.RESULT_ENABLE_FAST_FORWARD) -> {
                val enabled = data.getBooleanExtra(GameMenuContract.RESULT_ENABLE_FAST_FORWARD, false)
                fastForwardState.value = enabled
                MelonEmulator.setFastForwardEnabled(enabled)
            }
            // RESULT_CHEATS_CHANGED: cheats are edited in the shell database
            // by the menu and applied by the M3 adapter; nothing to do here.
        }
        resumeAfterMenu()
    }

    private fun resumeAfterMenu() {
        if (menuOpenedWhileRunning && emulationStarted && emulationPaused && !lifecyclePaused && !isFinishing) {
            MelonEmulator.resumeEmulation()
            emulationPaused = false
        }
        menuOpenedWhileRunning = false
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
        // Physical gamepad / keyboard menu key: opens the system menu on
        // press, like the libretro path (no hold needed for a real button).
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) openMenu()
            return true
        }
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
                fpsTextState.value = "显示 ${renderer.presentedFps} · 引擎 ${MelonEmulator.getFPS()} FPS"
                delay(1000)
            }
        }
    }

    // ------------------------------------------------- PadKit deck input

    /**
     * Maps the v3.3 PadKit deck's input events onto the engine. The game
     * buttons use the same KeyEvent codes as physical gamepads (see
     * [dispatchKeyEvent]); the deck-only buttons map to engine functions:
     * mic (hold to blow), lid-close (hold to close the DS lid). The menu
     * button opens the shared system menu on press — tap and long-press
     * alike, as the user asked (a press fires at touch-down either way).
     */
    private fun handlePadInputEvent(events: List<InputEvent>) {
        events.forEach { event ->
            when (event) {
                is InputEvent.Button -> {
                    when (event.id) {
                        KeyEvent.KEYCODE_BUTTON_A -> setPadInput(Input.A, event.pressed)
                        KeyEvent.KEYCODE_BUTTON_B -> setPadInput(Input.B, event.pressed)
                        KeyEvent.KEYCODE_BUTTON_X -> setPadInput(Input.X, event.pressed)
                        KeyEvent.KEYCODE_BUTTON_Y -> setPadInput(Input.Y, event.pressed)
                        KeyEvent.KEYCODE_BUTTON_L1 -> setPadInput(Input.L, event.pressed)
                        KeyEvent.KEYCODE_BUTTON_R1 -> setPadInput(Input.R, event.pressed)
                        KeyEvent.KEYCODE_BUTTON_START -> setPadInput(Input.START, event.pressed)
                        KeyEvent.KEYCODE_BUTTON_SELECT -> setPadInput(Input.SELECT, event.pressed)
                        KeyEvent.KEYCODE_BUTTON_L2 -> MelonEmulator.setMicrophoneEnabled(event.pressed)
                        KeyEvent.KEYCODE_BUTTON_THUMBL -> setPadInput(Input.HINGE, event.pressed)
                        KeyEvent.KEYCODE_BUTTON_MODE -> if (event.pressed) openMenu()
                        else -> Unit
                    }
                }
                is InputEvent.DiscreteDirection -> {
                    if (event.id == ComposeTouchLayouts.MOTION_SOURCE_DPAD) {
                        handlePadDirection(event.direction)
                    }
                }
                is InputEvent.ContinuousDirection -> Unit // The MELONDS deck has no sticks.
            }
        }
    }

    private fun setPadInput(input: Input, pressed: Boolean) {
        if (pressed) MelonEmulator.onInputDown(input) else MelonEmulator.onInputUp(input)
    }

    private fun handlePadDirection(direction: Offset) {
        // PadKit's cross handler already reports y-up-positive (it negates
        // the screen-space anchor), so up is +y here. The libretro path
        // negates it back for the core's motion events; the engine's
        // discrete inputs need no such step. Offset.Unspecified (no touch)
        // and Offset.Zero (dead zone) compare false against every threshold
        // below, so they correctly release all directions.
        val wanted =
            buildSet {
                if (direction.x < -0.5f) add(Input.LEFT)
                if (direction.x > 0.5f) add(Input.RIGHT)
                if (direction.y > 0.5f) add(Input.UP)
                if (direction.y < -0.5f) add(Input.DOWN)
            }
        (wanted - dpadPressed).forEach { MelonEmulator.onInputDown(it) }
        (dpadPressed - wanted).forEach { MelonEmulator.onInputUp(it) }
        dpadPressed.clear()
        dpadPressed.addAll(wanted)
    }

    private object NoOpRetroAchievementsCallback : RetroAchievementsCallback {
        override fun onAchievementPrimed(achievementId: Long) = Unit

        override fun onAchievementTriggered(achievementId: Long) = Unit

        override fun onAchievementUnprimed(achievementId: Long) = Unit
    }

    companion object {
        private const val EXTRA_GAME = "GAME"
        private const val EXTRA_SYSTEM_CORE_CONFIG = "EXTRA_SYSTEM_CORE_CONFIG"
        private const val MENU_REQUEST = 100
        private const val SCREEN_WIDTH = 256
        private const val SCREEN_HEIGHT = 384
        private const val DS_SCREEN_WIDTH = 256
        private const val DS_SCREEN_HEIGHT = 192

        fun launchGame(activity: Activity, game: Game, systemCoreConfig: SystemCoreConfig) {
            activity.startActivityForResult(
                Intent(activity, MelonDSGameActivity::class.java).apply {
                    putExtra(EXTRA_GAME, game)
                    putExtra(EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
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
