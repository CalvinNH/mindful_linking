package com.mindfullinking.app

import android.app.Application
import com.mindfullinking.core.notifications.NotificationScheduler

class MindfulLinkingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationScheduler.ensureChannelsCreated(this)
    }
}
