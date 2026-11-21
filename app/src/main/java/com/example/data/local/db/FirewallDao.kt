package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FirewallDao {

    @Query("SELECT * FROM firewall_rules ORDER BY isSystemApp ASC, appLabel ASC")
    fun observeAllRules(): Flow<List<FirewallRuleEntity>>

    @Query("SELECT * FROM firewall_rules")
    suspend fun getAllRulesSnapshot(): List<FirewallRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: FirewallRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(rules: List<FirewallRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRulesIfAbsent(rules: List<FirewallRuleEntity>)

    @Query("UPDATE firewall_rules SET allowWifi = :allow, allowCellular = :allow, updatedAt = :updatedAt WHERE packageName IN (:packageNames)")
    suspend fun updateRulesForPackages(
        packageNames: List<String>,
        allow: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE firewall_rules SET allowWifi = :allowWifi, updatedAt = :updatedAt WHERE packageName = :packageName")
    suspend fun updateWifiRule(packageName: String, allowWifi: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE firewall_rules SET allowCellular = :allowCellular, updatedAt = :updatedAt WHERE packageName = :packageName")
    suspend fun updateCellularRule(packageName: String, allowCellular: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE firewall_rules SET allowWifi = :allowAll, allowCellular = :allowAll, updatedAt = :updatedAt")
    suspend fun setAllRules(allowAll: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM blocked_attempts ORDER BY timestamp DESC LIMIT 200")
    fun observeRecentBlockedAttempts(): Flow<List<BlockedAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedAttempt(attempt: BlockedAttemptEntity)

    @Query("DELETE FROM blocked_attempts WHERE id NOT IN (SELECT id FROM blocked_attempts ORDER BY timestamp DESC LIMIT 300)")
    suspend fun pruneOldAttempts()

    @Query("DELETE FROM blocked_attempts")
    suspend fun clearBlockedAttempts()
}
