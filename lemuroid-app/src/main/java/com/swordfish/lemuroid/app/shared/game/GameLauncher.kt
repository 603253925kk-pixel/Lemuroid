package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.game.melonds.MelonDSGameActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GameLauncher(
    private val coresSelection: CoresSelection,
    private val gameLaunchTaskHandler: GameLaunchTaskHandler,
) {
    @OptIn(DelicateCoroutinesApi::class)
    fun launchGameAsync(
        activity: Activity,
        game: Game,
        loadSave: Boolean,
        leanback: Boolean,
    ): Boolean {
        if (GameProcessLock.isHeldByAnotherProcess(activity.applicationContext)) {
            activity.displayToast(R.string.game_process_another_game_running)
            return false
        }

        GlobalScope.launch {
            val system = GameSystem.findById(game.systemId)
            val coreConfig = coresSelection.getCoreConfigForSystem(system)
            gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
            if (shouldUseMelonDSEngine(activity, game, coreConfig, leanback)) {
                MelonDSGameActivity.launchGame(activity, game)
            } else {
                BaseGameActivity.launchGame(activity, coreConfig, game, loadSave, leanback)
            }
        }

        return true
    }

    /**
     * DS games whose selected core is melonDS are routed to the vendored
     * melonDS engine (GPU renderer) instead of the libretro path, unless the
     * user turned the engine off in the advanced settings. TV (leanback)
     * keeps the libretro path for now.
     */
    private fun shouldUseMelonDSEngine(
        activity: Activity,
        game: Game,
        coreConfig: SystemCoreConfig,
        leanback: Boolean,
    ): Boolean {
        if (leanback) return false
        if (game.systemId != SystemID.NDS.dbname) return false
        if (coreConfig.coreID != CoreID.MELONDS) return false
        val preferences = SharedPreferencesHelper.getSharedPreferences(activity.applicationContext)
        return preferences.getBoolean(activity.getString(R.string.pref_key_melonds_engine), true)
    }
}
