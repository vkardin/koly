package io.github.vkardin.koly

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.vkardin.koly.core.data.AppInfo
import io.github.vkardin.koly.core.data.AppUpdater
import io.github.vkardin.koly.core.data.SyncTrigger
import io.github.vkardin.koly.sync.SyncManager
import io.github.vkardin.koly.update.ReleaseUpdater

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun syncTrigger(impl: SyncManager): SyncTrigger

    @Binds
    abstract fun appUpdater(impl: ReleaseUpdater): AppUpdater

    companion object {
        @Provides
        fun appInfo(): AppInfo = AppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }
}
