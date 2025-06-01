package com.iqra.budslife.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.*

// Entity to store paired buds information
@Entity(tableName = "buds", indices = [Index("lastConnected")])
data class BudsEntity(
    @PrimaryKey val deviceAddress: String,
    val deviceName: String,
    val model: String? = null, // Model of the buds
    val manufacturer: String? = null, // Manufacturer of the buds
    val lastBatteryLevel: Int = 0,
    val batteryThreshold: Int = 35, // Default threshold for notifications
    val lastConnected: Date = Date(),
    val notificationsEnabled: Boolean = true
)

// Data Access Object (DAO) for database operations
@Dao
interface BudsDao {
    @Query("SELECT * FROM buds")
    fun getAllBuds(): Flow<List<BudsEntity>>

    @Query("SELECT * FROM buds WHERE deviceAddress = :deviceAddress LIMIT 1")
    fun getBudsByAddress(deviceAddress: String): Flow<BudsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuds(buds: BudsEntity)

    @Update
    suspend fun updateBuds(buds: BudsEntity)

    @Delete
    suspend fun deleteBuds(buds: BudsEntity)

    @Query("UPDATE buds SET lastBatteryLevel = :batteryLevel, lastConnected = :date WHERE deviceAddress = :deviceAddress")
    suspend fun updateBatteryLevel(deviceAddress: String, batteryLevel: Int, date: Date = Date())

    @Query("UPDATE buds SET batteryThreshold = :threshold WHERE deviceAddress = :deviceAddress")
    suspend fun updateBatteryThreshold(deviceAddress: String, threshold: Int)

    @Query("UPDATE buds SET notificationsEnabled = :enabled WHERE deviceAddress = :deviceAddress")
    suspend fun updateNotificationsEnabled(deviceAddress: String, enabled: Boolean)

    @Query("UPDATE buds SET model = :model, manufacturer = :manufacturer WHERE deviceAddress = :deviceAddress")
    suspend fun updateModelAndManufacturer(deviceAddress: String, model: String?, manufacturer: String?)
}

// Type converters for complex types that Room can't store directly
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

// The actual database class
@Database(entities = [BudsEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class BudsDatabase : RoomDatabase() {
    abstract fun budsDao(): BudsDao

    companion object {
        @Volatile
        private var INSTANCE: BudsDatabase? = null

        fun getDatabase(context: android.content.Context): BudsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    BudsDatabase::class.java,
                    "buds_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}