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
            return "$systemName $developerName"
        }

        // Play time gets its own line in the library (see LemuroidGameTexts):
        // appended to the subtitle it was truncated away in grid cards, which
        // made the feature effectively invisible.
        fun getPlayTimeText(game: Game): String? {
            return if (game.totalPlayTimeMillis > 0) {
                "Played ${formatPlayTime(game.totalPlayTimeMillis)}"
            } else {
                null
            }
        }

        fun formatPlayTime(totalPlayTimeMillis: Long): String {
            val totalMinutes = totalPlayTimeMillis / MILLIS_PER_MINUTE
            val hours = totalMinutes / MINUTES_PER_HOUR
            val minutes = totalMinutes % MINUTES_PER_HOUR
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "<1m"
            }
        }

        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MINUTES_PER_HOUR = 60L

        private fun getSystemNameForGame(
            context: Context,
            game: Game,
        ): String {
            val systemTitleResource = GameSystem.findById(game.systemId).shortTitleResId
            return context.getString(systemTitleResource)
        }
    }
}
