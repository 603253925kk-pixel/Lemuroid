package com.swordfish.lemuroid.lib.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(
    tableName = "cheats",
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("gameId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("id", unique = true),
        Index("gameId"),
    ],
)
data class Cheat(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val gameId: Int,
    val name: String,
    val code: String,
    val enabled: Boolean = true,
    val sortIndex: Int = 0,
) : Serializable
