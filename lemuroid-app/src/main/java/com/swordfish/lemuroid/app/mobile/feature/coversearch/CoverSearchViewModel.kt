package com.swordfish.lemuroid.app.mobile.feature.coversearch

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.mobile.feature.coversearch.LibretroCoverSearch.CoverCandidate
import com.swordfish.lemuroid.lib.library.covers.CoverLibraryManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.CoverLibraryItem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class CoverSearchViewModel(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val gameId: Int,
    private val coverLibraryManager: CoverLibraryManager,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val retrogradeDb: RetrogradeDatabase,
        private val gameId: Int,
        private val coverLibraryManager: CoverLibraryManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CoverSearchViewModel(appContext, retrogradeDb, gameId, coverLibraryManager) as T
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
        /** Covers saved in the material library, newest first. */
        val libraryCovers: List<CoverLibraryItem> = emptyList(),
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
    private var indexLoaded = false
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

            refreshLibraryCovers()
            searchAll()
        }
    }

    private fun refreshLibraryCovers() {
        viewModelScope.launch {
            runCatching { coverLibraryManager.listCovers() }
                .onSuccess { covers ->
                    uiStateFlow.value = uiStateFlow.value.copy(libraryCovers = covers)
                }
                .onFailure { Timber.w(it, "Failed to load the cover library") }
        }
    }

    fun onQueryChange(newQuery: String) {
        uiStateFlow.value = uiStateFlow.value.copy(query = newQuery)
    }

    /** Runs a search with the current keywords across every cover source. */
    fun search() {
        if (uiStateFlow.value.isLoadingList) {
            return
        }
        viewModelScope.launch {
            searchAll()
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
                // The cover that gets applied is also saved into the
                // material library, so it can be reused on other games.
                // Best-effort: a library failure must not fail the apply.
                runCatching {
                    coverLibraryManager.saveCover(
                        tempFile,
                        candidate.displayName,
                        candidate.fullImageUrl,
                        game.systemId,
                    )
                }.onSuccess { refreshLibraryCovers() }
                    .onFailure { Timber.w(it, "Failed to save cover to the library") }
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

    /** Applies a cover from the material library (no download involved). */
    fun selectLibraryCover(item: CoverLibraryItem) {
        val game = uiStateFlow.value.game ?: return
        if (uiStateFlow.value.isApplying) {
            return
        }
        viewModelScope.launch {
            uiStateFlow.value =
                uiStateFlow.value.copy(isApplying = true, applyError = false)
            runCatching {
                val libraryFile = coverLibraryManager.coverFile(item)
                if (!libraryFile.exists()) {
                    throw IllegalStateException("Library cover file is gone: ${item.fileName}")
                }
                val tempFile =
                    File(
                        appContext.cacheDir,
                        "cover-search/cover_${game.id}.jpg",
                    )
                tempFile.parentFile?.mkdirs()
                libraryFile.copyTo(tempFile, overwrite = true)
                Uri.fromFile(tempFile).toString()
            }.onSuccess { localUri ->
                uiStateFlow.value =
                    uiStateFlow.value.copy(isApplying = false, appliedCoverUri = localUri)
            }.onFailure { error ->
                Timber.e(error, "Failed to apply library cover")
                uiStateFlow.value =
                    uiStateFlow.value.copy(isApplying = false, applyError = true)
            }
        }
    }

    /** Marks the applied-cover event as consumed by the screen. */
    fun onCoverApplied() {
        uiStateFlow.value = uiStateFlow.value.copy(appliedCoverUri = null)
    }

    /** Local file URI of a library cover, for the screen's image loader. */
    fun libraryCoverUri(item: CoverLibraryItem): String =
        Uri.fromFile(coverLibraryManager.coverFile(item)).toString()

    /**
     * Queries all cover sources in parallel and merges their candidates:
     * the libretro thumbnails library first (authoritative boxart, ranked
     * locally against the cached directory index), then web image search
     * (Baidu, then Bing), which is where Chinese-titled and modified ROMs
     * can be found at all. A source that fails only removes itself; the
     * error state is shown solely when every source failed.
     */
    private suspend fun searchAll() {
        val repository = repositoryName ?: return
        val query = uiStateFlow.value.query
        // Web image search gets the system as an extra keyword ("NDS",
        // "GBA", ...) so generic titles stop matching posters, books and
        // wallpapers instead of boxart.
        val systemToken = uiStateFlow.value.game?.systemId?.uppercase().orEmpty()
        uiStateFlow.value =
            uiStateFlow.value.copy(isLoadingList = true, listError = false)

        var anySourceSucceeded = false
        val merged =
            coroutineScope {
                val libretro =
                    async {
                        runCatching {
                            if (!indexLoaded) {
                                allFileNames =
                                    LibretroCoverSearch.fetchBoxartFileNames(httpClient, repository)
                                indexLoaded = true
                            }
                            LibretroCoverSearch.rankCandidates(allFileNames, repository, query)
                        }
                    }
                val baidu = async { runCatching { WebCoverSearch.searchBaidu(httpClient, query, systemToken) } }
                val bing = async { runCatching { WebCoverSearch.searchBing(httpClient, query, systemToken) } }

                listOf(libretro.await(), baidu.await(), bing.await())
                    .onEach { result ->
                        result
                            .onSuccess { anySourceSucceeded = true }
                            .onFailure { Timber.w(it, "A cover source failed") }
                    }
                    .flatMap { it.getOrDefault(emptyList()) }
                    .distinctBy { it.fullImageUrl }
            }

        uiStateFlow.value =
            uiStateFlow.value.copy(
                isLoadingList = false,
                listError = merged.isEmpty() && !anySourceSucceeded,
                candidates = merged.take(MAX_TOTAL_RESULTS),
            )
    }

    private companion object {
        const val MAX_TOTAL_RESULTS = 48
    }
}
