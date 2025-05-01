package com.example.spotan.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "played_items")
data class DbPlayedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val trackName: String,
    val artistName: String,
    val durationMs: Int,
    val playedAt: String,
    val coverUrl: String? = null,        // Новое поле для URL обложки альбома
    val artistCoverUrl: String? = null
)
