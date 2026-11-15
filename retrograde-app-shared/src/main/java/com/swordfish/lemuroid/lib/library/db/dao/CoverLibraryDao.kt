package com.swordfish.lemuroid.lib.library.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.swordfish.lemuroid.lib.library.db.entity.CoverLibraryItem

@Dao
interface CoverLibraryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(item: CoverLibraryItem): Long

    @Query("SELECT * FROM cover_library ORDER BY createdAt DESC")
    suspend fun selectAll(): List<CoverLibraryItem>

    @Query("SELECT * FROM cover_library WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun selectBySourceUrl(sourceUrl: String): CoverLibraryItem?

    @Delete
    suspend fun delete(item: CoverLibraryItem)
}
