package org.pcbu.libreclient.storage.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: PairedDeviceEntity)

    @Update
    suspend fun update(device: PairedDeviceEntity)

    @Delete
    suspend fun delete(device: PairedDeviceEntity)

    @Query("DELETE FROM paired_devices WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)

    @Query("SELECT * FROM paired_devices WHERE deviceId = :deviceId")
    suspend fun findById(deviceId: String): PairedDeviceEntity?

    @Query("SELECT * FROM paired_devices ORDER BY COALESCE(NULLIF(customName, ''), NULLIF(userName, ''), deviceName) COLLATE NOCASE, deviceName")
    fun observeAll(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE deviceId = :deviceId")
    fun observeById(deviceId: String): Flow<PairedDeviceEntity?>

    @Query("SELECT * FROM paired_devices ORDER BY COALESCE(NULLIF(customName, ''), NULLIF(userName, ''), deviceName) COLLATE NOCASE, deviceName")
    suspend fun getAll(): List<PairedDeviceEntity>

    @Query("UPDATE paired_devices SET macroStepsJson = :macroStepsJson WHERE deviceId = :deviceId")
    suspend fun updateMacroSteps(deviceId: String, macroStepsJson: String?)

    @Query("UPDATE paired_devices SET customName = :customName WHERE deviceId = :deviceId")
    suspend fun updateCustomName(deviceId: String, customName: String?)
}
