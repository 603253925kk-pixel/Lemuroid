package com.swordfish.lemuroid.app.shared.game.melonds

import android.content.Context
import android.os.Build
import me.magnum.melonds.domain.model.AudioBitrate
import me.magnum.melonds.domain.model.AudioInterpolation
import me.magnum.melonds.domain.model.AudioLatency
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.EmulatorConfiguration
import me.magnum.melonds.domain.model.FirmwareConfiguration
import me.magnum.melonds.domain.model.MicSource
import me.magnum.melonds.domain.model.RendererConfiguration
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.domain.model.VideoRenderer

/**
 * Builds the [EmulatorConfiguration] used by the vendored melonDS engine.
 *
 * Values mirror the upstream melonDS-android defaults (see
 * SharedPreferencesSettingsRepository in the beta-1.10.0 tree), with two
 * deliberate deviations:
 * - the renderer defaults to [VideoRenderer.OPENGL]: GPU rendering is the
 *   whole point of this engine path (upstream's preference default is
 *   "software" for compatibility reasons). The user can flip to software
 *   rendering in the advanced settings — added as an A/B switch after the
 *   #50 device test showed water surfaces rendering orange on the OpenGL
 *   path in Pokémon Origin HeartGold, so the two renderers can be compared
 *   in a single build;
 * - threaded rendering is OFF (it was on through #50): the threaded 3D
 *   renderer is the prime suspect for the wrong water textures, and
 *   upstream ships it as an opt-in for the same class of glitches;
 * - no custom BIOS: the core falls back to its built-in FreeBIOS and
 *   internally generated firmware, so the user never has to provide files.
 */
object MelonDSEmulatorConfig {
    fun build(
        context: Context,
        useSoftwareRenderer: Boolean = false,
    ): EmulatorConfiguration {
        return EmulatorConfiguration(
            useCustomBios = false,
            showBootScreen = false,
            dsBios7Uri = null,
            dsBios9Uri = null,
            dsFirmwareUri = null,
            dsiBios7Uri = null,
            dsiBios9Uri = null,
            dsiFirmwareUri = null,
            dsiNandUri = null,
            internalDirectory = context.filesDir.absolutePath,
            fastForwardSpeedMultiplier = -1f,
            rewindEnabled = false,
            rewindPeriodSeconds = 10,
            rewindWindowSeconds = 60,
            useJit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            consoleType = ConsoleType.DS,
            soundEnabled = true,
            audioInterpolation = AudioInterpolation.NONE,
            audioBitrate = AudioBitrate.AUTO,
            volume = 256,
            audioLatency = AudioLatency.MEDIUM,
            micSource = MicSource.BLOW,
            firmwareConfiguration =
                FirmwareConfiguration(
                    nickname = "Player",
                    message = "Hello!",
                    language = 1,
                    favouriteColour = 0,
                    birthdayMonth = 1,
                    birthdayDay = 1,
                    randomizeMacAddress = true,
                    internalMacAddress = null,
                ),
            rendererConfiguration =
                RendererConfiguration(
                    renderer = if (useSoftwareRenderer) VideoRenderer.SOFTWARE else VideoRenderer.OPENGL,
                    videoFiltering = VideoFiltering.NONE,
                    threadedRendering = false,
                    // 3D internal resolution 3x (was 1x, then 2x in #53): the #52
                    // device test showed evenly spaced thin dark horizontal
                    // bands on the top (3D) screen. Frame analysis of the
                    // user's recording proved the bands already exist at
                    // native 256x192 resolution — a ~14-texel-row periodic
                    // structure whose period varies with depth (perspective),
                    // i.e. the dark edge rows of the game's own repeating
                    // ground/rock textures, rendered 1:1 with nearest
                    // sampling and then magnified 4.2x by the presentation
                    // layer. The Kotlin UV split and GL_LINEAR presentation
                    // were verified correct (texture is 256x386 = 192+2+192;
                    // the split matches to <1e-4), so no sampling fix
                    // applies there. Rendering the 3D layer at 2x (#53)
                    // softened the bands but the user still sees them on
                    // device, so this goes to 3x (768x576): the compositor
                    // gets 3x subpixel precision and the tile edges are
                    // sampled at three times the rate. Adreno 750-class GPUs
                    // handle DS 3D at 3x; the software path is unaffected
                    // (it renders at 1x). Honest caveat: the tile borders
                    // are real texture content, so 3x weakens them further
                    // but may still not erase them completely — if lines
                    // persist on device, the next lever is a
                    // presentation-side smoothing filter, not a higher scale.
                    internalResolutionScaling = 3,
                ),
        )
    }
}
