package com.example.spotan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayedItemDao {

    @Query("SELECT * FROM played_items ORDER BY playedAt ASC")
    suspend fun getAll(): List<DbPlayedItem>
    @Query("DELETE FROM played_items")
    suspend fun clearAll()
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<DbPlayedItem>)
    @Query("""
    SELECT * FROM played_items
    ORDER BY playedAt DESC
    LIMIT :limit
  """)
    suspend fun getRecent(limit: Int = 100): List<DbPlayedItem>
}