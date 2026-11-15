package com.swordfish.lemuroid.lib.library.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

/**
 * One cover saved into the in-app material library (素材库).
 *
 * Covers that the user applies from online search are copied here so they can
 * be reused on other games without downloading again. The file itself lives
 * in [com.swordfish.lemuroid.lib.library.covers.CoverLibraryManager.getLibraryDirectory]
 * under [fileName]; this row is only the index. [sourceUrl] is unique so the
 * same cover is never saved twice.
 */
@Entity(
    tableName = "cover_library",
    indices = [
        Index("id", unique = true),
        Index("sourceUrl", unique = true),
        Index("createdAt"),
    ],
)
data class CoverLibraryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fileName: String,
    val title: String,
    val sourceUrl: String,
    val systemId: String,
    val createdAt: Long,
) : Serializable
