package com.swordfish.lemuroid.app.shared.game.melonds

import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SaveInfo
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Engine-native savestate slots for the vendored melonDS engine.
 *
 * Mirrors [com.swordfish.lemuroid.lib.saves.StatesManager] in shape (4 slots,
 * `<rom>.slot<N>` files) but lives in a separate `melonds-engine` folder
 * under the states directory: the libretro states in `states/melonds/` are a
 * different format and the two must never be mixed — an engine state loaded
 * as a libretro state (or the reverse) would corrupt the session. The engine
 * writes its own state blobs via `MelonEmulator.saveState` /
 * `MelonEmulator.loadState`; this class only owns the slot files.
 */
class MelonDSStatesManager(private val directoriesManager: DirectoriesManager) {
    suspend fun getSlotsInfo(game: Game): List<SaveInfo> =
        withContext(Dispatchers.IO) {
            (0 until MAX_STATES)
                .map { stateFile(game, it) }
                .map { SaveInfo(it.exists() && it.length() > 0, it.lastModified()) }
        }

    fun stateFile(game: Game, slot: Int): File {
        require(slot in 0 until MAX_STATES) { "Invalid state slot: $slot" }
        val statesDirectory = File(directoriesManager.getStatesDirectory(), ENGINE_STATES_DIR)
        statesDirectory.mkdirs()
        return File(statesDirectory, "${game.fileName}.slot${slot + 1}")
    }

    companion object {
        const val MAX_STATES = 4
        private const val ENGINE_STATES_DIR = "melonds-engine"
    }
}
