package app.cristal.cover.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HomeItemEntity::class, AppPrefEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CristalDatabase : RoomDatabase() {
    abstract fun homeDao(): HomeDao
    abstract fun appPrefDao(): AppPrefDao
}
