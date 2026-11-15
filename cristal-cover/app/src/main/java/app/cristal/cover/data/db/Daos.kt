package app.cristal.cover.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeDao {
    @Query("SELECT * FROM home_items ORDER BY container, page, cellY, cellX, id")
    fun observeAll(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items ORDER BY container, page, cellY, cellX, id")
    suspend fun getAll(): List<HomeItemEntity>

    @Query("SELECT * FROM home_items WHERE id = :id")
    suspend fun getById(id: Long): HomeItemEntity?

    @Query("SELECT * FROM home_items WHERE container = :container ORDER BY page, cellY, cellX, id")
    suspend fun getByContainer(container: Long): List<HomeItemEntity>

    @Query("SELECT COUNT(*) FROM home_items")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HomeItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HomeItemEntity>)

    @Update
    suspend fun update(item: HomeItemEntity)

    @Update
    suspend fun updateAll(items: List<HomeItemEntity>)

    @Query("DELETE FROM home_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM home_items WHERE container = :container")
    suspend fun deleteByContainer(container: Long)

    @Query("DELETE FROM home_items WHERE componentKey LIKE :packagePrefix || '/%'")
    suspend fun deleteByPackage(packagePrefix: String)

    @Query("DELETE FROM home_items")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<HomeItemEntity>) {
        clear()
        insertAll(items)
    }
}

@Dao
interface AppPrefDao {
    @Query("SELECT * FROM app_prefs")
    fun observeAll(): Flow<List<AppPrefEntity>>

    @Query("SELECT * FROM app_prefs")
    suspend fun getAll(): List<AppPrefEntity>

    @Query("SELECT * FROM app_prefs WHERE componentKey = :key")
    suspend fun get(key: String): AppPrefEntity?

    @Upsert
    suspend fun upsert(pref: AppPrefEntity)

    @Upsert
    suspend fun upsertAll(prefs: List<AppPrefEntity>)

    @Query("DELETE FROM app_prefs")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(prefs: List<AppPrefEntity>) {
        clear()
        upsertAll(prefs)
    }
}
