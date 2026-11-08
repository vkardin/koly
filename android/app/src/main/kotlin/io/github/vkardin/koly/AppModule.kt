package io.github.vkardin.koly

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.vkardin.koly.core.data.AppInfo
import io.github.vkardin.koly.core.data.SyncTrigger
import io.github.vkardin.koly.sync.SyncManager

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun syncTrigger(impl: SyncManager): SyncTrigger

    companion object {
        @Provides
        fun appInfo(): AppInfo = AppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }
}
