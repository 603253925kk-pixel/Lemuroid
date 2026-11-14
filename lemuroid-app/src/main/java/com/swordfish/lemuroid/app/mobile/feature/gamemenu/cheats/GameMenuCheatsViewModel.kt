package com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.lib.library.CheatsManager
import com.swordfish.lemuroid.lib.library.db.entity.Cheat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameMenuCheatsViewModel(
    private val gameMenuRequest: GameMenuActivity.GameMenuRequest,
    private val cheatsManager: CheatsManager,
) : ViewModel() {
    class Factory(
        private val gameMenuRequest: GameMenuActivity.GameMenuRequest,
        private val cheatsManager: CheatsManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameMenuCheatsViewModel(gameMenuRequest, cheatsManager) as T
        }
    }

    data class State(val cheats: List<Cheat> = emptyList())

    private val uiStateFlow = MutableStateFlow(State())
    val uiState: StateFlow<State> = uiStateFlow

    init {
        viewModelScope.launch {
            refreshCheats()
        }
    }

    fun addCheat(
        name: String,
        code: String,
    ) {
        viewModelScope.launch {
            cheatsManager.addCheat(gameMenuRequest.game.id, name, code)
            refreshCheats()
        }
    }

    fun updateCheat(
        cheat: Cheat,
        name: String,
        code: String,
    ) {
        viewModelScope.launch {
            cheatsManager.updateCheat(cheat.copy(name = name, code = code))
            refreshCheats()
        }
    }

    fun setCheatEnabled(
        cheat: Cheat,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            cheatsManager.setCheatEnabled(cheat, enabled)
            refreshCheats()
        }
    }

    fun deleteCheat(cheat: Cheat) {
        viewModelScope.launch {
            cheatsManager.deleteCheat(cheat)
            refreshCheats()
        }
    }

    private suspend fun refreshCheats() {
        uiStateFlow.value = State(cheatsManager.getCheats(gameMenuRequest.game.id))
    }
}
