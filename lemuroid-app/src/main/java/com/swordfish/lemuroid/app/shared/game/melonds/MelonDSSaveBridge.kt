package com.swordfish.lemuroid.app.shared.game.melonds

import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.io.File

/**
 * Bridges save RAM between the host and the vendored melonDS engine.
 *
 * The host keeps battery saves in its saves directory under RetroArch naming
 * (`<rom name>.srm`, see SavesManager). The engine addresses its SRAM file by
 * URI and follows the melonDS convention (`<rom name>.sav`). Both formats are
 * the same raw melonDS SRAM dump, so bridging is a plain byte copy:
 *
 * - before a session, the host save is copied to the engine name if it is the
 *   newest copy (first-launch migration of existing saves);
 * - during/after a session, the engine save is copied back to the host name
 *   whenever it changed, so the libretro path and save sync keep seeing the
 *   latest progress.
 *
 * "Newest wins" is decided by last-modified time; originals are never deleted.
 */
class MelonDSSaveBridge(private val savesDirectory: File) {
    fun hostSaveFile(game: Game): File = File(savesDirectory, saveBaseName(game) + ".srm")

    fun engineSaveFile(game: Game): File = File(savesDirectory, saveBaseName(game) + ".sav")

    private fun saveBaseName(game: Game): String = game.fileName.substringBeforeLast(".")

    /** Returns the engine-side save file, migrated from the host save if needed. */
    fun migrateToEngine(game: Game): File {
        val hostSave = hostSaveFile(game)
        val engineSave = engineSaveFile(game)
        if (hostSave.exists() && hostSave.length() > 0) {
            if (!engineSave.exists() || hostSave.lastModified() > engineSave.lastModified()) {
                engineSave.writeBytesAtomic(hostSave.readBytes())
            }
        }
        return engineSave
    }

    /** Copies the engine save back over the host save if the engine one is newer. */
    fun syncBackToHost(game: Game) {
        val hostSave = hostSaveFile(game)
        val engineSave = engineSaveFile(game)
        if (engineSave.exists() && engineSave.length() > 0) {
            if (!hostSave.exists() || engineSave.lastModified() > hostSave.lastModified()) {
                hostSave.writeBytesAtomic(engineSave.readBytes())
            }
        }
    }
}
