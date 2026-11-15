package com.swordfish.lemuroid.lib.library.covers

import android.content.Context
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.CoverLibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * The in-app material library (素材库) for covers, first asset type.
 *
 * Every cover the user applies from online search is copied here, so it can
 * be picked again on another game without downloading twice. Files live in
 * app-private external storage under [getLibraryDirectory], named by the
 * SHA-1 of their source URL; the Room table only indexes them (and dedups
 * by source URL). Nothing here ever touches the network.
 */
class CoverLibraryManager(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
) {
    /** Returns (and creates if necessary) the directory for library covers. */
    fun getLibraryDirectory(): File =
        File(appContext.getExternalFilesDir(null), "cover-library").apply {
            mkdirs()
        }

    /** Newest first, capped so the search screen stays light. */
    suspend fun listCovers(limit: Int = MAX_LIBRARY_COVERS): List<CoverLibraryItem> =
        withContext(Dispatchers.IO) {
            retrogradeDb.coverLibraryDao().selectAll().take(limit)
        }

    /**
     * Saves [sourceFile] (the already-downloaded, downscaled JPEG) into the
     * library. Returns the new or existing row, or null when anything fails
     * — callers treat the library as best-effort and must never fail an
     * in-progress cover apply because of it.
     */
    suspend fun saveCover(
        sourceFile: File,
        title: String,
        sourceUrl: String,
        systemId: String,
    ): CoverLibraryItem? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dao = retrogradeDb.coverLibraryDao()
                dao.selectBySourceUrl(sourceUrl)?.let { return@runCatching it }

                val fileName = "cover_${sha1(sourceUrl)}.jpg"
                val destFile = File(getLibraryDirectory(), fileName)
                if (!destFile.exists()) {
                    sourceFile.copyTo(destFile, overwrite = true)
                }
                val item =
                    CoverLibraryItem(
                        fileName = fileName,
                        title = title,
                        sourceUrl = sourceUrl,
                        systemId = systemId,
                        createdAt = System.currentTimeMillis(),
                    )
                val rowId = dao.insert(item)
                if (rowId == -1L) {
                    // Lost a race with a concurrent insert of the same URL.
                    dao.selectBySourceUrl(sourceUrl)
                } else {
                    item.copy(id = rowId.toInt())
                }
            }.onFailure { Timber.w(it, "Failed to save cover to the library") }
                .getOrNull()
        }

    /** Local file for a library row. */
    fun coverFile(item: CoverLibraryItem): File = File(getLibraryDirectory(), item.fileName)

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MAX_LIBRARY_COVERS = 12
    }
}
