package com.example.data.repository

import com.example.data.local.av.OpenSourceAvEngine
import com.example.data.local.db.AntivirusDao
import com.example.domain.model.AppEntry
import com.example.domain.model.AvScanDepth
import com.example.domain.model.AvScanReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AntivirusRepository @Inject constructor(
    private val antivirusDao: AntivirusDao,
    private val avEngine: OpenSourceAvEngine
) {
    val cachedReports: Flow<List<AvScanReport>> = antivirusDao.observeAllScans()
        .map { list -> list.map { avEngine.entityToReport(it, fromCache = true) } }
        .flowOn(Dispatchers.IO)

    suspend fun scanApp(
        app: AppEntry,
        depth: AvScanDepth,
        useCache: Boolean,
        includeAdsAudit: Boolean,
        forceRefresh: Boolean = false
    ): AvScanReport? = avEngine.scanPackage(
        packageName = app.packageName,
        depth = depth,
        inWorkProfile = app.isInstalledInWorkProfile,
        useCache = useCache,
        includeAdsAudit = includeAdsAudit,
        forceRefresh = forceRefresh
    )

    suspend fun scanPackageName(
        packageName: String,
        depth: AvScanDepth,
        inWorkProfile: Boolean,
        useCache: Boolean,
        includeAdsAudit: Boolean,
        forceRefresh: Boolean = false
    ): AvScanReport? = avEngine.scanPackage(
        packageName = packageName,
        depth = depth,
        inWorkProfile = inWorkProfile,
        useCache = useCache,
        includeAdsAudit = includeAdsAudit,
        forceRefresh = forceRefresh
    )

    suspend fun analyzeAds(app: AppEntry): AvScanReport? =
        avEngine.analyzeAdsOnly(
            packageName = app.packageName,
            inWorkProfile = app.isInstalledInWorkProfile
        )

    suspend fun clearScanCache() = withContext(Dispatchers.IO) {
        antivirusDao.clearAllScans()
    }
}
