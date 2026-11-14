package com.swordfish.lemuroid.app.shared.game.melonds

import android.os.SystemClock
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Play time accounting for the melonDS engine path, mirroring the semantics
 * established for the libretro path in v3.4 (BaseGameScreenViewModel):
 * increments are flushed to the database every minute while playing and once
 * more when the session is paused or torn down, so a session is not lost when
 * the process is killed in the background. Each flush also refreshes
 * `lastPlayedAt`, re-reading the stored game first so concurrent writers
 * never double-count.
 */
class MelonDSPlayTimeTracker(
    private val game: Game,
    private val retrogradeDb: RetrogradeDatabase,
    private val scope: CoroutineScope,
) {
    private val sessionStartElapsed = SystemClock.elapsedRealtime()
    private var lastFlushElapsed = sessionStartElapsed
    private val flushMutex = Mutex()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            touchLastPlayed()
            while (true) {
                delay(FLUSH_INTERVAL_MILLIS)
                runCatching { flush() }
            }
        }
    }

    suspend fun flush() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val deltaMillis = nowElapsed - lastFlushElapsed
        if (deltaMillis <= 0) return
        lastFlushElapsed = nowElapsed
        flushMutex.withLock {
            withContext(Dispatchers.IO) {
                val storedGame = retrogradeDb.gameDao().selectById(game.id) ?: return@withContext
                retrogradeDb.gameDao().update(
                    storedGame.copy(
                        lastPlayedAt = System.currentTimeMillis(),
                        totalPlayTimeMillis = storedGame.totalPlayTimeMillis + deltaMillis,
                    ),
                )
            }
        }
    }

    private suspend fun touchLastPlayed() {
        withContext(Dispatchers.IO) {
            val storedGame = retrogradeDb.gameDao().selectById(game.id) ?: return@withContext
            retrogradeDb.gameDao().update(
                storedGame.copy(lastPlayedAt = System.currentTimeMillis()),
            )
        }
    }

    companion object {
        private const val FLUSH_INTERVAL_MILLIS = 60_000L
    }
}
