package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "firewall_rules")
data class FirewallRuleEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val uid: Int,
    val isSystemApp: Boolean,
    val allowWifi: Boolean,
    val allowCellular: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocked_attempts")
data class BlockedAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val uid: Int,
    val destinationIp: String,
    val destinationPort: Int,
    val protocol: String,
    val networkType: String,
    val timestamp: Long = System.currentTimeMillis()
)
