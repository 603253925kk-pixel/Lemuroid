package com.swordfish.lemuroid.app.shared.game.melonds

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.swordfish.lemuroid.app.mobile.feature.game.ShellColorTheme
import timber.log.Timber

/**
 * How a game's picture fits its system's screen (smart adaptation
 * resolves it per game at launch).
 *
 * The approach follows the long-standing emulator convention — RetroArch's
 * aspect-ratio modes ("Core Provided" keeps the core's aspect, "Full"
 * stretches to the window) and melonDS-Android's own screen-layout
 * choices — reimplemented here as original code: the renderer maps the
 * core frame onto the shell window by quad geometry (FIT = uniform scale,
 * FILL = independent x/y scales), and the bottom-screen touch transform
 * normalizes through the same rects, so touch stays aligned.
 *
 * Per-system principle: the fit is resolved carrying the game's system.
 * This enum and its resolution live on the NDS dual-screen path only
 * (NDS games → NDS resolver + NDS seed DB); GBA's libretro single screen
 * is already fitted to the 3:2 GBA screen and never touches it, and a
 * future Switch path must resolve its own 16:9 fit. Never cross-apply
 * one system's layout to another's games.
 */
enum class ScreenFitMode(val id: String) {
    /** Keep the core frame's aspect; black bars where the window differs. */
    FIT("fit"),

    /** Stretch the frame to the whole screen window; may distort slightly. */
    FILL("fill");

    companion object {
        fun fromId(id: String?): ScreenFitMode =
            entries.firstOrNull { it.id == id?.lowercase() } ?: FIT
    }
}

/**
 * Smart adaptation runtime (slice 6): per-game starting settings plus a
 * closed tuning loop that runs on the phone, inside the game session.
 *
 * How it works:
 * 1. At launch the ROM header game code is read; the seed database gives
 *    that game's starting resolution/JIT (IPKE = 3x, everything else
 *    2x in the v1 seeds), and a previously learned stable resolution
 *    for the same game overrides the start level. Unknown games start
 *    at the 3x default. The same lookup also resolves the game's
 *    screen fit mode (seed "fit", else FIT) and its shell color theme
 *    (seed "shell" override, else title keywords, else silver) — see
 *    [SmartAdaptation.resolveShellTheme].
 * 2. While playing, the tuner watches the renderer's *presented* FPS
 *    (frames actually shown, not the engine's produced-frame counter —
 *    the #48 test proved the engine counter reads 60 during visible
 *    stutter). Sustained stutter steps the 3D resolution down one
 *    level (3→2→1); sustained headroom steps it back up, never above
 *    the game's target. A level held for 15 s is saved as the game's
 *    learned stable resolution for the next launch.
 * 3. The resolution change goes through
 *    `MelonEmulator.updateEmulatorConfiguration`, which the native
 *    layer applies on the next frame (`GPU::SetRenderSettings`) — no
 *    restart, no reload. The presentation filter's texel size follows
 *    via `MelonDSRenderer.internalResolutionScale`.
 *
 * Guards: the first 10 s after launch are ignored (loading/shader
 * warm-up stutters); samples are dropped while paused, in the menu or
 * fast-forwarding (fast-forward uncaps the frame rate, so it would
 * fake headroom); step-downs need 3 consecutive low samples, step-ups
 * need 10 consecutive full-speed samples, and every change has a
 * cooldown, so the level cannot oscillate. The software renderer
 * always runs at 1x, so the tuner is a no-op there.
 */
data class AdaptationSettings(
    /** Resolution level the game starts at (1..3). */
    val startResolution: Int,
    /** Highest resolution the tuner may step back up to (1..3). */
    val targetResolution: Int,
    /** JIT on/off at launch (engine setting, cannot change at runtime). */
    val useJit: Boolean,
    /**
     * Fingerprint-database title for the launch toast, if the game is
     * known (No-Intro title, e.g. "Pokemon - HeartGold Version (USA,
     * Australia)" — the database holds no Chinese names, so the toast
     * shortens this to the part before the region parenthesis). Null for
     * an unknown game; the toast then falls back to the game code.
     */
    val gameTitle: String? = null,
    /**
     * Learned stable resolution that actually lowered this launch's start
     * level (non-null only when a stored learned profile exists and is
     * below the target, so start == learned). Null when no profile
     * exists, or when the learned level equals the target (nothing was
     * lowered). The launch toast names it when present.
     */
    val learnedResolution: Int? = null,
    /**
     * How this game's picture fits the shell's screen (seed override,
     * else FIT). Carried with the game's system — NDS only here.
     */
    val fitMode: ScreenFitMode = ScreenFitMode.FIT,
    /**
     * Shell color theme for this game (seed override, else title
     * keywords, else classic silver). Colors only — no layout change.
     */
    val shellTheme: ShellColorTheme = ShellColorTheme.SILVER,
)

object SmartAdaptation {
    const val DEFAULT_RESOLUTION = 3
    val DEFAULT_USE_JIT: Boolean get() = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

    /**
     * Resolves the starting settings for a game. Pure lookup — call it
     * from the launch IO thread (it loads the asset databases on first use).
     *
     * [userTargetOverride] is the Settings "NDS 目标分辨率" choice (null
     * = 自动）: when set, it replaces the seed database's target for this
     * game; the learned profile still caps the start level at
     * min(learned, target).
     */
    fun resolveInitial(
        context: Context,
        prefs: SharedPreferences,
        gameCode: String?,
        userTargetOverride: Int? = null,
    ): AdaptationSettings {
        val seed = gameCode?.let { SmartAdaptationDatabase.seedFor(context, it) }
        val targetResolution =
            (userTargetOverride ?: seed?.settings?.internalResolution ?: DEFAULT_RESOLUTION)
                .coerceIn(1, 3)
        val useJit = seed?.settings?.jit ?: DEFAULT_USE_JIT
        val learned =
            gameCode?.let { SmartAdaptationStore.learnedResolution(prefs, it) }?.coerceIn(1, 3)
        val startResolution = minOf(learned ?: targetResolution, targetResolution)
        val learnedApplied =
            if (learned != null && learned < targetResolution) learned else null
        val gameTitle =
            gameCode?.let { SmartAdaptationDatabase.fingerprintFor(context, it)?.title }
                ?.takeIf { it.isNotBlank() }
        val fitMode =
            seed?.settings?.fit?.let { ScreenFitMode.fromId(it) } ?: ScreenFitMode.FIT
        val shellTheme = resolveShellTheme(seed, gameTitle)
        if (gameCode != null) {
            val title = gameTitle ?: "unknown game"
            Timber.i(
                "Smart adaptation: $gameCode ($title) start=${startResolution}x " +
                    "target=${targetResolution}x jit=$useJit learned=$learned " +
                    "fit=${fitMode.id} shell=${shellTheme.id}",
            )
        }
        return AdaptationSettings(
            startResolution,
            targetResolution,
            useJit,
            gameTitle,
            learnedApplied,
            fitMode,
            shellTheme,
        )
    }

    /**
     * Resolves the shell color theme for a game. Priority: the seed
     * database's per-game override ("shell"), then the fingerprint
     * title's keywords, else classic silver.
     *
     * The themes render Nintendo's real hardware colors (DS Lite Polar
     * White, DSi Matte Black, DS Lite Cobalt/Black and Crimson/Black —
     * see [ShellColorTheme]); the keyword mapping only picks which real
     * color a game gets, it never invents a color. An unknown seed id
     * falls through to the keyword rule instead of forcing silver.
     */
    fun resolveShellTheme(
        seed: SmartAdaptationSeedGame?,
        gameTitle: String?,
    ): ShellColorTheme {
        seed?.shell?.lowercase()?.let { id ->
            if (ShellColorTheme.entries.any { it.id == id }) {
                return ShellColorTheme.fromId(id)
            }
        }
        val title = gameTitle?.lowercase() ?: return ShellColorTheme.SILVER
        return when {
            "pokemon" in title || "宝可梦" in title -> ShellColorTheme.PEARL
            "mario" in title || "马里奥" in title -> ShellColorTheme.CHERRY
            "zelda" in title || "塞尔达" in title -> ShellColorTheme.OCEAN
            "kirby" in title || "星之卡比" in title -> ShellColorTheme.CHERRY
            "animal crossing" in title || "动物之森" in title -> ShellColorTheme.PEARL
            else -> ShellColorTheme.SILVER
        }
    }
}

/** Per-game learned stable resolution, kept in the shared preferences. */
object SmartAdaptationStore {
    private const val KEY_PREFIX = "smart_adaptation_learned_resolution_"

    fun learnedResolution(prefs: SharedPreferences, gameCode: String): Int? {
        val value = prefs.getInt(KEY_PREFIX + gameCode, 0)
        return if (value in 1..3) value else null
    }

    fun saveLearnedResolution(prefs: SharedPreferences, gameCode: String, resolution: Int) {
        if (resolution !in 1..3) return
        prefs.edit().putInt(KEY_PREFIX + gameCode, resolution).apply()
        Timber.i("Smart adaptation: learned stable resolution for $gameCode is ${resolution}x")
    }
}

/**
 * The runtime tuning loop. Feed it one presented-FPS sample per second
 * from the activity's FPS update loop; it calls back only when the
 * resolution actually changes.
 */
class SmartAdaptationTuner(
    private val gameCode: String?,
    private val prefs: SharedPreferences,
    targetResolution: Int,
    startResolution: Int,
    private val isSoftwareRenderer: Boolean,
    private val onResolutionChanged: (newResolution: Int) -> Unit,
    /** Fires once per session, on the first adaptation step, for a toast. */
    private val onFirstAdaptation: (newResolution: Int) -> Unit,
) {
    private val target = targetResolution.coerceIn(1, 3)

    private var resolution = startResolution.coerceIn(1, 3)
    private var startedAtMs = -1L
    private var lastChangeMs = 0L
    private var cooldownUntilMs = 0L
    private var levelHeldSinceMs = 0L
    private var lowSamples = 0
    private var highSamples = 0
    private var lastPersistedResolution = -1
    private var firstAdaptationReported = false

    fun currentResolution(): Int = resolution

    fun onFpsSample(presentedFps: Int, canTune: Boolean) {
        if (isSoftwareRenderer) return
        val nowMs = System.currentTimeMillis()
        if (startedAtMs < 0) {
            startedAtMs = nowMs
            lastChangeMs = nowMs
            levelHeldSinceMs = nowMs
            return
        }
        if (!canTune) {
            // Paused, in the menu or fast-forwarding: the sample means
            // nothing, so drop it instead of counting it as headroom.
            lowSamples = 0
            highSamples = 0
            return
        }
        if (presentedFps <= 0) return
        if (nowMs - startedAtMs < WARMUP_MS) return

        if (presentedFps < STEP_DOWN_FPS) {
            lowSamples++
            highSamples = 0
        } else if (presentedFps >= STEP_UP_FPS) {
            highSamples++
            lowSamples = 0
        } else {
            lowSamples = 0
            highSamples = 0
        }

        if (nowMs < cooldownUntilMs) {
            persistIfStable(nowMs)
            return
        }

        if (lowSamples >= STEP_DOWN_SAMPLES && resolution > MIN_RESOLUTION) {
            applyLevel(resolution - 1, nowMs, STEP_DOWN_COOLDOWN_MS)
            return
        }
        if (highSamples >= STEP_UP_SAMPLES && resolution < target) {
            applyLevel(resolution + 1, nowMs, STEP_UP_COOLDOWN_MS)
            return
        }
        persistIfStable(nowMs)
    }

    private fun applyLevel(newResolution: Int, nowMs: Long, cooldownMs: Long) {
        resolution = newResolution
        lastChangeMs = nowMs
        cooldownUntilMs = nowMs + cooldownMs
        levelHeldSinceMs = nowMs
        lowSamples = 0
        highSamples = 0
        Timber.i("Smart adaptation: resolution -> ${newResolution}x (target ${target}x)")
        onResolutionChanged(newResolution)
        if (!firstAdaptationReported) {
            firstAdaptationReported = true
            onFirstAdaptation(newResolution)
        }
    }

    private fun persistIfStable(nowMs: Long) {
        if (gameCode == null) return
        if (nowMs - levelHeldSinceMs < STABLE_MS) return
        if (resolution == lastPersistedResolution) return
        lastPersistedResolution = resolution
        SmartAdaptationStore.saveLearnedResolution(prefs, gameCode, resolution)
    }

    companion object {
        /** Loading/shader warm-up: ignore stutter right after launch. */
        private const val WARMUP_MS = 10_000L

        /** Step down below this presented FPS (DS target is 60). */
        private const val STEP_DOWN_FPS = 50

        /** Step up only at (near-)full speed. */
        private const val STEP_UP_FPS = 59

        /** Consecutive low samples before stepping down (3 s). */
        private const val STEP_DOWN_SAMPLES = 3

        /** Consecutive full-speed samples before stepping up (10 s). */
        private const val STEP_UP_SAMPLES = 10

        private const val STEP_DOWN_COOLDOWN_MS = 5_000L
        private const val STEP_UP_COOLDOWN_MS = 10_000L

        /** A level held this long counts as stable and is learned. */
        private const val STABLE_MS = 15_000L

        private const val MIN_RESOLUTION = 1
    }
}
