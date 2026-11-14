package com.swordfish.lemuroid.lib.library.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.swordfish.lemuroid.lib.library.db.entity.Cheat

@Dao
interface CheatDao {
    @Query("SELECT * FROM cheats WHERE gameId = :gameId ORDER BY sortIndex ASC, id ASC")
    suspend fun selectCheatsForGame(gameId: Int): List<Cheat>

    @Insert
    suspend fun insert(cheat: Cheat): Long

    @Update
    suspend fun update(cheat: Cheat)

    @Delete
    suspend fun delete(cheat: Cheat)
}
