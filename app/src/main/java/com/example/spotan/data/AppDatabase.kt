package com.example.spotan.data
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DbPlayedItem::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playedItemDao(): PlayedItemDao
}