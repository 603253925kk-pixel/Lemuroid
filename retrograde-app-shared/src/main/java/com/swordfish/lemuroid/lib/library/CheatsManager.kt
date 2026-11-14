package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.lib.library.db.dao.CheatDao
import com.swordfish.lemuroid.lib.library.db.entity.Cheat

class CheatsManager(private val cheatDao: CheatDao) {
    suspend fun getCheats(gameId: Int): List<Cheat> {
        return cheatDao.selectCheatsForGame(gameId)
    }

    suspend fun addCheat(
        gameId: Int,
        name: String,
        code: String,
    ): Cheat {
        val existingCheats = cheatDao.selectCheatsForGame(gameId)
        val cheat =
            Cheat(
                gameId = gameId,
                name = name,
                code = code,
                enabled = true,
                sortIndex = existingCheats.size,
            )
        val id = cheatDao.insert(cheat)
        return cheat.copy(id = id.toInt())
    }

    suspend fun updateCheat(cheat: Cheat) {
        cheatDao.update(cheat)
    }

    suspend fun setCheatEnabled(
        cheat: Cheat,
        enabled: Boolean,
    ) {
        cheatDao.update(cheat.copy(enabled = enabled))
    }

    suspend fun deleteCheat(cheat: Cheat) {
        cheatDao.delete(cheat)
        reassignSortIndexes(cheat.gameId)
    }

    private suspend fun reassignSortIndexes(gameId: Int) {
        cheatDao.selectCheatsForGame(gameId)
            .forEachIndexed { index, cheat ->
                if (cheat.sortIndex != index) {
                    cheatDao.update(cheat.copy(sortIndex = index))
                }
            }
    }

    companion object {
        /**
         * Normalizes a user-entered cheat code to the format expected by libretro cores:
         * whitespace is stripped from each line and multiple lines are joined with '+'.
         * The raw code, as typed by the user, is what gets stored in the database.
         */
        fun normalizeCode(code: String): String {
            return code.lines()
                .map { line -> line.filterNot { char -> char.isWhitespace() } }
                .filter { it.isNotEmpty() }
                .joinToString("+")
        }

        fun isValidCode(code: String): Boolean {
            val normalizedCode = normalizeCode(code)
            return normalizedCode.isNotEmpty() &&
                normalizedCode.all { char ->
                    char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F' || char == '+'
                }
        }
    }
}
