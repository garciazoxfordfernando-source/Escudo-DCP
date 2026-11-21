package com.example.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FirewallRuleEntity::class,
        BlockedAttemptEntity::class,
        AntivirusScanEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class EscudoDatabase : RoomDatabase() {
    abstract fun firewallDao(): FirewallDao
    abstract fun antivirusDao(): AntivirusDao
}
