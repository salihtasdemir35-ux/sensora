package com.sensora.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One saved measurement. Location columns stay null unless the user explicitly opts in. */
@Entity(tableName = "measurements")
data class MeasurementRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolId: String,
    val category: String,
    val value: Double,
    val unit: String,
    val details: String? = null,
    val method: String,
    val quality: String,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val note: String? = null,
    val favorite: Boolean = false,
)

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MeasurementRecord>>

    @Insert
    suspend fun insert(record: MeasurementRecord): Long

    @Update
    suspend fun update(record: MeasurementRecord)

    @Delete
    suspend fun delete(record: MeasurementRecord)

    @Query("DELETE FROM measurements")
    suspend fun clear()
}

@Database(entities = [MeasurementRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
}

class HistoryRepository(private val dao: MeasurementDao) {
    fun observeAll(): Flow<List<MeasurementRecord>> = dao.observeAll()
    suspend fun add(record: MeasurementRecord) = dao.insert(record)
    suspend fun update(record: MeasurementRecord) = dao.update(record)
    suspend fun delete(record: MeasurementRecord) = dao.delete(record)
    suspend fun clear() = dao.clear()
}
