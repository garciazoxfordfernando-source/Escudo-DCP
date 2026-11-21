package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AntivirusDao {

    @Query("SELECT * FROM antivirus_scans ORDER BY threatScore DESC, appLabel ASC")
    fun observeAllScans(): Flow<List<AntivirusScanEntity>>

    @Query("SELECT * FROM antivirus_scans WHERE packageName = :packageName LIMIT 1")
    suspend fun getScanForPackage(packageName: String): AntivirusScanEntity?

    @Query("SELECT * FROM antivirus_scans")
    suspend fun getAllScansSnapshot(): List<AntivirusScanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScan(entity: AntivirusScanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AntivirusScanEntity>)

    @Query("DELETE FROM antivirus_scans")
    suspend fun clearAllScans()
}
