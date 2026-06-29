package com.drakosanctis.auriga.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AurigaApplication : Application() {

    companion object {
        const val COPILOT_NOTIFICATION_CHANNEL_ID = "auriga_copilot_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                COPILOT_NOTIFICATION_CHANNEL_ID,
                "Auriga Co-Pilot",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while Auriga is actively watching for hazards."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
