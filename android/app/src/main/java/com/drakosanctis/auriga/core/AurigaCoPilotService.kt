package com.drakosanctis.auriga.core

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps Auriga's camera/detection pipeline alive
 * even if the user switches apps briefly (e.g. to silence a phone call) —
 * an assistive co-pilot that stops working the instant the screen locks
 * would defeat its own purpose.
 *
 * Actual pipeline ownership lives in MainActivity/AurigaPipeline for MVP v1
 * simplicity (this service mainly exists to satisfy Android's foreground
 * service + notification requirements for sustained camera use). A future
 * version can move pipeline ownership fully into this service if background
 * operation needs to extend beyond brief app-switches.
 */
class AurigaCoPilotService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, AurigaApplication.COPILOT_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Auriga is watching")
            .setContentText("Your co-pilot is active and monitoring for hazards.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
