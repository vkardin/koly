package io.github.vkardin.koly.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.vkardin.koly.core.data.AppInfo
import io.github.vkardin.koly.core.data.BuildConfig
import io.github.vkardin.koly.core.data.db.CacheDao
import io.github.vkardin.koly.core.data.db.HistoryDao
import io.github.vkardin.koly.core.data.db.KolyDatabase
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): KolyDatabase =
        Room.databaseBuilder(context, KolyDatabase::class.java, "koly.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun cacheDao(db: KolyDatabase): CacheDao = db.cacheDao()

    @Provides
    fun historyDao(db: KolyDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Named("dataBaseUrl")
    fun dataBaseUrl(): String = BuildConfig.DATA_BASE_URL

    @Provides
    @Singleton
    fun httpClient(appInfo: AppInfo): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "KolyYe/${appInfo.versionName} (+https://vkardin.github.io)")
                    .build(),
            )
        }
        .build()
}
