package com.swordfish.lemuroid.app.mobile.feature.gamemenu.states

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.game.melonds.MelonDSStatesManager
import com.swordfish.lemuroid.app.shared.gamemenu.GameMenuHelper
import kotlinx.coroutines.flow.flow

/**
 * Slot list for the vendored melonDS engine's own savestates.
 *
 * Mirrors [GameMenuStatesViewModel] (same titles, same date descriptions,
 * same enable rules) but reads the engine-native slots owned by
 * [MelonDSStatesManager] instead of the libretro [StatesManager] slots:
 * the two formats are not interchangeable, so the engine menu must never
 * list libretro states. There is no preview bitmap: the engine does not
 * capture one with its states.
 */
class MelonDSEngineStatesViewModel(
    private val application: Application,
    private val gameMenuRequest: GameMenuActivity.GameMenuRequest,
    private val statesManager: MelonDSStatesManager,
    private val disableMissingEntries: Boolean,
) : ViewModel() {
    class Factory(
        private val application: Application,
        private val gameMenuRequest: GameMenuActivity.GameMenuRequest,
        private val statesManager: MelonDSStatesManager,
        private val disableMissingEntries: Boolean,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MelonDSEngineStatesViewModel(
                application,
                gameMenuRequest,
                statesManager,
                disableMissingEntries,
            ) as T
        }
    }

    data class StateEntry(
        val title: String,
        val description: String,
        val enabled: Boolean,
    )

    data class State(val entries: List<StateEntry> = emptyList())

    val uiStates =
        flow {
            val slotsInfo = statesManager.getSlotsInfo(gameMenuRequest.game)

            val entries =
                slotsInfo.mapIndexed { index, slotInfo ->
                    val title =
                        application.applicationContext.getString(
                            R.string.game_menu_state,
                            (index + 1).toString(),
                        )
                    val description = GameMenuHelper.getSaveStateDescription(slotInfo)
                    val isEnabled = !disableMissingEntries || slotInfo.exists

                    StateEntry(title, description, isEnabled)
                }

            emit(State(entries))
        }
}
