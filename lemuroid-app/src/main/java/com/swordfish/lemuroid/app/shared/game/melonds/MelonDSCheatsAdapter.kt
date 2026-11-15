package com.swordfish.lemuroid.app.shared.game.melonds

import com.swordfish.lemuroid.lib.library.db.entity.Cheat as LemuroidCheat
import me.magnum.melonds.domain.model.Cheat as EngineCheat
import timber.log.Timber

/**
 * Converts Lemuroid's stored cheats to the vendored melonDS engine's cheat
 * model (M3) and documents the two models' differences.
 *
 * Lemuroid stores the code exactly as the user typed it (multi-line, spaces
 * allowed); [com.swordfish.lemuroid.lib.library.CheatsManager.normalizeCode]
 * turns it into `+`-joined hex for libretro cores. The engine instead wants
 * space-separated 8-hex-digit words — its JNI bridge splits the code string
 * on spaces and rejects any section that is not exactly 8 hex characters
 * (see `MelonEmulator_setupCheats` in MelonDSAndroidJNI.cpp), and its
 * `Cheat.isValid()` caps a code at 128 words.
 *
 * Two engine quirks the conversion works around:
 * - The native layer reads only the `code` field and hardcodes every
 *   passed cheat to Enabled — so a disabled cheat must be *omitted* from
 *   the array, not passed with `enabled = false`.
 * - The native layer used to early-return on an empty array, so turning
 *   off the last enabled cheat could never take effect mid-session; the
 *   JNI bridge now forwards empty arrays to `setCodeList` (which clears
 *   the list), so [toEngineCheats] returning an empty array is a real
 *   "cheats off", not a no-op.
 *
 * Codes that cannot be represented as 8-hex-digit words (odd length, a
 * non-hex character, more than 128 words) are dropped and logged — never
 * passed to the engine, where a malformed section would be silently
 * skipped per cheat anyway. The menu already rejects non-hex codes on
 * entry ([com.swordfish.lemuroid.lib.library.CheatsManager.isValidCode]),
 * so in practice only odd-length codes hit this path.
 */
object MelonDSCheatsAdapter {
    fun toEngineCheats(cheats: List<LemuroidCheat>): Array<EngineCheat> {
        return cheats
            .filter { it.enabled }
            .mapNotNull { cheat ->
                val engineCode = toEngineCode(cheat.code)
                if (engineCode == null) {
                    Timber.w(
                        "Skipping cheat '${cheat.name}' (id=${cheat.id}): " +
                            "code does not convert to 8-hex-digit AR words",
                    )
                    null
                } else {
                    EngineCheat(
                        id = null,
                        cheatDatabaseId = cheat.id.toLong(),
                        name = cheat.name,
                        description = null,
                        code = engineCode,
                        enabled = true,
                    )
                }
            }
            .toTypedArray()
    }

    /**
     * Converts one stored code to the engine's format (`"01234567
     * 89ABCDEF"`), or null when it cannot be represented as whole 8-digit
     * words. Whitespace and the `+` separators of the libretro normalized
     * form are stripped first; the remaining hex is chunked into words.
     */
    fun toEngineCode(rawCode: String): String? {
        val hex = rawCode.filter { !it.isWhitespace() && it != '+' }
        if (hex.isEmpty()) return null
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        if (hex.length % 8 != 0) return null
        val parts = hex.chunked(8)
        if (parts.size > 128) return null
        return parts.joinToString(" ")
    }
}
