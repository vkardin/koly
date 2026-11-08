package io.github.vkardin.koly

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.vkardin.koly.feature.settings.AppLanguage
import io.github.vkardin.koly.notify.Notifier
import io.github.vkardin.koly.sync.SyncWork
import javax.inject.Inject

@HiltAndroidApp
class KolyApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Ukrainian is the default whatever the system language is; it can be changed in onboarding.
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        if (!prefs.getBoolean("language_initialized", false)) {
            prefs.edit().putBoolean("language_initialized", true).apply()
            AppLanguage.set("uk")
        }
        notifier.createChannels()
        SyncWork.schedulePeriodic(this)
    }
}
