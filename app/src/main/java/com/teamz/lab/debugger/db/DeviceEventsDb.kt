package com.teamz.lab.debugger.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * v3.2.0 R5 foundation — the Device Timeline store (2026-07-10 growth synthesis).
 *
 * ONE local table that every event-driven feature writes into:
 *   - score_scan          (FirstScanGate + LastScoreCard replays)
 *   - charge_session      (R2 charge report ritual)
 *   - app_installed       (R4 new-app watchdog)
 *   - baseline_snapshot   (daily health snapshot for R3 widget deltas)
 *
 * This table IS the retention moat: uninstalling = losing the device's medical
 * record. Free UI shows 7 days; Premium unlocks 30/90 days (the packaging the
 * premium tier already promises). History is collected regardless of tier —
 * the paywall unlocks the VIEW, it never gates the collection.
 *
 * Local-only. No network. 90-day pruning keeps the DB bounded.
 */
@Entity(tableName = "device_events")
data class DeviceEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val timestamp: Long,
    val score: Int? = null,
    val subBattery: Int? = null,
    val subMemory: Int? = null,
    val subStorage: Int? = null,
    val subNetwork: Int? = null,
    /** One human-readable line, pre-rendered by the writer ("Charged 62→100% in 1h 40m"). */
    val label: String? = null,
    /** Optional JSON extras (charge watts curve, package name, etc). */
    val payload: String? = null,
) {
    companion object {
        const val TYPE_SCORE_SCAN = "score_scan"
        const val TYPE_CHARGE_SESSION = "charge_session"
        const val TYPE_APP_INSTALLED = "app_installed"
        const val TYPE_BASELINE_SNAPSHOT = "baseline_snapshot"
    }
}

@Dao
interface DeviceEventDao {
    @Insert
    suspend fun insert(event: DeviceEvent): Long

    @Query("SELECT * FROM device_events WHERE timestamp >= :sinceTs ORDER BY timestamp DESC")
    suspend fun eventsSince(sinceTs: Long): List<DeviceEvent>

    @Query("SELECT * FROM device_events WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun latestByType(type: String, limit: Int): List<DeviceEvent>

    @Query("SELECT * FROM device_events WHERE type = :type AND timestamp >= :sinceTs ORDER BY timestamp DESC")
    suspend fun byTypeSince(type: String, sinceTs: Long): List<DeviceEvent>

    @Query("SELECT COUNT(*) FROM device_events")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM device_events WHERE type = :type AND timestamp >= :sinceTs")
    suspend fun countByTypeSince(type: String, sinceTs: Long): Int

    @Query("SELECT COUNT(*) FROM device_events WHERE type = :type AND timestamp >= :fromTs AND timestamp < :toTs")
    suspend fun countByTypeBetween(type: String, fromTs: Long, toTs: Long): Int

    @Query("DELETE FROM device_events WHERE timestamp < :olderThanTs")
    suspend fun prune(olderThanTs: Long): Int
}

@Database(entities = [DeviceEvent::class], version = 1, exportSchema = false)
abstract class DeviceGptDatabase : RoomDatabase() {
    abstract fun deviceEventDao(): DeviceEventDao

    companion object {
        @Volatile
        private var instance: DeviceGptDatabase? = null

        fun get(context: Context): DeviceGptDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DeviceGptDatabase::class.java,
                    "device_gpt.db"
                )
                    // v1 is the first schema — no legacy migrations exist. If a future
                    // downgrade ever happens (sideload of an older APK), losing this
                    // local cache is acceptable; nothing here is source-of-truth.
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                    .also { instance = it }
            }
    }
}
