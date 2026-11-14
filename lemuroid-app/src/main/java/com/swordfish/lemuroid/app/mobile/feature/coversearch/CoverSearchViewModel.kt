package com.swordfish.lemuroid.app.mobile.feature.coversearch

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.mobile.feature.coversearch.LibretroCoverSearch.CoverCandidate
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class CoverSearchViewModel(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val gameId: Int,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val retrogradeDb: RetrogradeDatabase,
        private val gameId: Int,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CoverSearchViewModel(appContext, retrogradeDb, gameId) as T
        }
    }

    data class UiState(
        val game: Game? = null,
        val query: String = "",
        val candidates: List<CoverCandidate> = emptyList(),
        val isLoadingList: Boolean = true,
        val listError: Boolean = false,
        val isApplying: Boolean = false,
        val applyError: Boolean = false,
        val appliedCoverUri: String? = null,
    )

    private val uiStateFlow = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = uiStateFlow

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /** Directory index of the game's system, downloaded once and re-ranked locally. */
    private var allFileNames: List<String> = emptyList()
    private var repositoryName: String? = null

    init {
        viewModelScope.launch {
            val game = retrogradeDb.gameDao().selectById(gameId)
            if (game == null) {
                uiStateFlow.value =
                    UiState(isLoadingList = false, listError = true)
                return@launch
            }

            repositoryName = LibretroCoverSearch.repositoryNameFor(game)
            val initialQuery =
                LibretroCoverSearch.cleanSearchTitle(game.title)
                    .ifBlank { LibretroCoverSearch.cleanSearchTitle(game.fileName) }
            uiStateFlow.value =
                uiStateFlow.value.copy(game = game, query = initialQuery)

            fetchIndexAndRank()
        }
    }

    fun onQueryChange(newQuery: String) {
        uiStateFlow.value = uiStateFlow.value.copy(query = newQuery)
    }

    /**
     * Runs a search with the current keywords. When the directory index has not
     * been downloaded yet (first run or after a failure) it is fetched first;
     * otherwise the cached index is simply re-ranked locally.
     */
    fun search() {
        if (uiStateFlow.value.isLoadingList) {
            return
        }
        viewModelScope.launch {
            if (allFileNames.isEmpty()) {
                fetchIndexAndRank()
            } else {
                rankWithCurrentQuery()
            }
        }
    }

    fun selectCandidate(candidate: CoverCandidate) {
        val game = uiStateFlow.value.game ?: return
        if (uiStateFlow.value.isApplying) {
            return
        }
        viewModelScope.launch {
            uiStateFlow.value =
                uiStateFlow.value.copy(isApplying = true, applyError = false)
            runCatching {
                val tempFile =
                    File(
                        appContext.cacheDir,
                        "cover-search/cover_${game.id}.jpg",
                    )
                LibretroCoverSearch.downloadCoverAsJpegFile(httpClient, candidate, tempFile)
                Uri.fromFile(tempFile).toString()
            }.onSuccess { localUri ->
                uiStateFlow.value =
                    uiStateFlow.value.copy(isApplying = false, appliedCoverUri = localUri)
            }.onFailure { error ->
                Timber.e(error, "Failed to download selected cover")
                uiStateFlow.value =
                    uiStateFlow.value.copy(isApplying = false, applyError = true)
            }
        }
    }

    /** Marks the applied-cover event as consumed by the screen. */
    fun onCoverApplied() {
        uiStateFlow.value = uiStateFlow.value.copy(appliedCoverUri = null)
    }

    private suspend fun fetchIndexAndRank() {
        val repository = repositoryName ?: return
        uiStateFlow.value =
            uiStateFlow.value.copy(isLoadingList = true, listError = false)
        runCatching {
            LibretroCoverSearch.fetchBoxartFileNames(httpClient, repository)
        }.onSuccess { fileNames ->
            allFileNames = fileNames
            rankWithCurrentQuery()
        }.onFailure { error ->
            Timber.e(error, "Failed to fetch boxart index for $repository")
            uiStateFlow.value =
                uiStateFlow.value.copy(isLoadingList = false, listError = true)
        }
    }

    private suspend fun rankWithCurrentQuery() {
        val repository = repositoryName ?: return
        val query = uiStateFlow.value.query
        val candidates =
            withContext(Dispatchers.Default) {
                LibretroCoverSearch.rankCandidates(allFileNames, repository, query)
            }
        uiStateFlow.value =
            uiStateFlow.value.copy(
                isLoadingList = false,
                listError = false,
                candidates = candidates,
            )
    }
}
