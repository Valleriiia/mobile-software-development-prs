package app.energymonitor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {

    @Insert
    suspend fun insert(entity: ReadingEntity)

    // REPLACE потрібен для кешу: запис із тим самим id, що прийшов із сервера,
    // має перезаписати застарілу локальну копію, а не викликати помилку.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ReadingEntity>)

    @Update
    suspend fun update(entity: ReadingEntity)

    @Query("SELECT * FROM readings ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<ReadingEntity>>

    @Query("SELECT COUNT(*) FROM readings")
    suspend fun count(): Int

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM readings")
    suspend fun clear()
}
