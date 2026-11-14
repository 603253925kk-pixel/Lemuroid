package com.swordfish.lemuroid.app.utils.games

import android.content.Context
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game

class GameUtils {
    companion object {
        fun getGameSubtitle(
            context: Context,
            game: Game,
        ): String {
            val systemName = getSystemNameForGame(context, game)
            val developerName =
                if (game.developer?.isNotBlank() == true) {
                    "- ${game.developer}"
                } else {
                    ""
                }
            val playTime =
                if (game.totalPlayTimeMillis >= MIN_PLAY_TIME_MILLIS) {
                    " - Played ${formatPlayTime(game.totalPlayTimeMillis)}"
                } else {
                    ""
                }
            return "$systemName $developerName$playTime"
        }

        private fun formatPlayTime(totalPlayTimeMillis: Long): String {
            val totalMinutes = totalPlayTimeMillis / MILLIS_PER_MINUTE
            val hours = totalMinutes / MINUTES_PER_HOUR
            val minutes = totalMinutes % MINUTES_PER_HOUR
            return if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes}m"
            }
        }

        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MINUTES_PER_HOUR = 60L
        private const val MIN_PLAY_TIME_MILLIS = 60_000L

        private fun getSystemNameForGame(
            context: Context,
            game: Game,
        ): String {
            val systemTitleResource = GameSystem.findById(game.systemId).shortTitleResId
            return context.getString(systemTitleResource)
        }
    }
}
