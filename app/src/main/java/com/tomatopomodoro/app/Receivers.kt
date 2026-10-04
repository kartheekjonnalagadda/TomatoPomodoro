package com.tomatopomodoro.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PomodoroEngine.load(context)
        if (PomodoroEngine.currentRemaining() > 1500L && PomodoroEngine.state.value.running) return
        PomodoroEngine.complete(context)
        val open = PendingIntent.getActivity(
            context, 20, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            android.app.NotificationChannel(
                TimerService.ALARM_CHANNEL,
                context.getString(R.string.channel_alarm),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        val notification = NotificationCompat.Builder(context, TimerService.ALARM_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tomato)
            .setContentTitle(context.getString(R.string.notif_done))
            .setContentText("Time to take a break.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        nm.notify(77, notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PomodoroEngine.load(context)
        val s = PomodoroEngine.state.value
        if (s.running && s.endWallMs > System.currentTimeMillis()) {
            AlarmScheduler.schedule(context, s.endWallMs)
            ContextCompatStart.startTimer(context)
        }
    }
}
