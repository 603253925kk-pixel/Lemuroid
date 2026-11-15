package com.swordfish.lemuroid.app.mobile.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.StatesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads one game plus its save-state summary for the detail page: how many
 * of the 4 instant-save slots are used, and whether an auto-save exists.
 * Save states live per core, so the core currently selected for the game's
 * system is resolved first; anything unresolvable degrades to "no saves"
 * instead of failing the page.
 */
class GameDetailViewModel(
    private val retrogradeDb: RetrogradeDatabase,
    private val coresSelection: CoresSelection,
    private val statesManager: StatesManager,
    private val gameId: Int,
) : ViewModel() {
    class Factory(
        val retrogradeDb: RetrogradeDatabase,
        val coresSelection: CoresSelection,
        val statesManager: StatesManager,
        val gameId: Int,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameDetailViewModel(retrogradeDb, coresSelection, statesManager, gameId) as T
        }
    }

    data class UIState(
        val game: Game? = null,
        val saveSlotsUsed: Int = 0,
        val hasAutoSave: Boolean = false,
        val loading: Boolean = true,
    )

    private val uiStates = MutableStateFlow(UIState())

    fun getViewStates(): Flow<UIState> = uiStates

    init {
        viewModelScope.launch {
            val game =
                withContext(Dispatchers.IO) {
                    retrogradeDb.gameDao().selectById(gameId)
                }
            if (game == null) {
                uiStates.value = UIState(loading = false)
                return@launch
            }
            val (slotsUsed, hasAutoSave) =
                runCatching {
                    val coreID =
                        coresSelection
                            .getCoreConfigForSystem(GameSystem.findById(game.systemId))
                            .coreID
                    val slots = statesManager.getSavedSlotsInfo(game, coreID)
                    val autoSave = statesManager.getAutoSaveInfo(game, coreID)
                    slots.count { it.exists } to autoSave.exists
                }.getOrDefault(0 to false)
            uiStates.value =
                UIState(
                    game = game,
                    saveSlotsUsed = slotsUsed,
                    hasAutoSave = hasAutoSave,
                    loading = false,
                )
        }
    }
}
