package app.energymonitor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {

    @Insert
    suspend fun insert(entity: ReadingEntity)

    @Query("SELECT * FROM readings ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<ReadingEntity>>

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun deleteById(id: Long)
}
