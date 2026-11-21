package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "antivirus_scans")
data class AntivirusScanEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val isSystemApp: Boolean,
    val inWorkProfile: Boolean,
    val sha256: String,
    val installerPackage: String?,
    val signerSummary: String,
    val verdict: String,
    val threatScore: Int,
    val scanDepth: String,
    val findingsJson: String,
    val adSdksJson: String,
    val scanDurationMs: Long,
    val scannedAt: Long
)
