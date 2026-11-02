package com.adochi

import android.app.Application
import com.adochi.core.AppSettings
import com.adochi.core.FilterRepository
import com.adochi.core.MessageGuard
import com.adochi.core.Stats
import com.adochi.core.UpdateWorker
import com.adochi.core.WeeklyReportWorker
import com.adochi.vpn.Notifications

class AdOchiApp : Application() {

    lateinit var settings: AppSettings
        private set
    lateinit var filters: FilterRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = AppSettings(this)
        Stats.init(this)
        MessageGuard.init(this)
        filters = FilterRepository(this, settings)
        Notifications.createChannels(this)

        filters.loadAsync()
        UpdateWorker.schedulePeriodic(this)
        WeeklyReportWorker.schedule(this)
        if (settings.lastUpdate == 0L) UpdateWorker.runNow(this)
    }

    companion object {
        lateinit var instance: AdOchiApp
            private set
    }
}
