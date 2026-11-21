package com.example.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.data.local.db.AntivirusDao
import com.example.data.local.db.EscudoDatabase
import com.example.data.local.db.FirewallDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.escudoDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "escudo_encrypted_prefs"
)

@Module
@InstallIn(SingletonComponent::class)
object EscudoModules {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EscudoDatabase {
        return Room.databaseBuilder(
            context,
            EscudoDatabase::class.java,
            "escudo_firewall.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides
    fun provideFirewallDao(database: EscudoDatabase): FirewallDao {
        return database.firewallDao()
    }

    @Provides
    fun provideAntivirusDao(database: EscudoDatabase): AntivirusDao {
        return database.antivirusDao()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.escudoDataStore
    }
}
