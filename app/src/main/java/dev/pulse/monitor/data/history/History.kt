package dev.pulse.monitor.data.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.pulse.monitor.data.model.BatterySnapshot
import kotlinx.coroutines.flow.Flow

/** One logged point: written every ~15 minutes by [dev.pulse.monitor.work.HistoryWorker]. */
@Entity(tableName = "history", indices = [Index(value = ["timestamp"])])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: Int,
    val temperatureC: Float?,
    val charging: Boolean,
    val plugged: Boolean,
    val powerW: Float?,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query("SELECT * FROM history WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun observeSince(since: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun since(since: Long): List<HistoryEntity>

    @Query("DELETE FROM history WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT MAX(timestamp) FROM history")
    suspend fun latestTimestamp(): Long?
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): PulseDatabase =
            Room.databaseBuilder(context.applicationContext, PulseDatabase::class.java, "pulse.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

class HistoryRepository(private val dao: HistoryDao) {

    fun observeSince(sinceMs: Long): Flow<List<HistoryEntity>> = dao.observeSince(sinceMs)

    suspend fun since(sinceMs: Long): List<HistoryEntity> = dao.since(sinceMs)

    suspend fun latestTimestamp(): Long? = dao.latestTimestamp()

    /** Logs a snapshot and deletes anything older than 7 days. Returns false if no level. */
    suspend fun record(snapshot: BatterySnapshot): Boolean {
        val level = snapshot.levelPercent ?: return false
        dao.insert(
            HistoryEntity(
                timestamp = snapshot.timestampMs,
                level = level,
                temperatureC = snapshot.temperatureC,
                charging = snapshot.isCharging,
                plugged = snapshot.isPlugged,
                powerW = snapshot.powerW,
            )
        )
        dao.deleteOlderThan(snapshot.timestampMs - RETENTION_MS)
        return true
    }

    companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
