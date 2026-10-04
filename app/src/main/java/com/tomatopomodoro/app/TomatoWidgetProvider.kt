package com.tomatopomodoro.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class TomatoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PomodoroEngine.load(context)
        ids.forEach { WidgetUpdater.bind(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        PomodoroEngine.load(context)
        when (intent.action) {
            ACTION_TOGGLE -> PomodoroEngine.toggle(context)
            ACTION_PLUS -> PomodoroEngine.nudge(context, 5)
            ACTION_MINUS -> PomodoroEngine.nudge(context, -5)
        }
        WidgetUpdater.refresh(context)
    }

    companion object {
        const val ACTION_TOGGLE = "com.tomatopomodoro.app.TOGGLE"
        const val ACTION_PLUS = "com.tomatopomodoro.app.PLUS"
        const val ACTION_MINUS = "com.tomatopomodoro.app.MINUS"
    }
}

object WidgetUpdater {
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TomatoWidgetProvider::class.java))
        ids.forEach { bind(context, manager, it) }
    }

    fun bind(context: Context, manager: AppWidgetManager, id: Int) {
        val s = PomodoroEngine.state.value
        val left = PomodoroEngine.currentRemaining()
        val views = RemoteViews(context.packageName, R.layout.widget_tomato)
        views.setTextViewText(R.id.widget_time, TimerService.format(left))
        views.setTextViewText(
            R.id.widget_label,
            if (s.running) "Running · ${s.durationMinutes} min" else "${s.durationMinutes} min · tap tomato",
        )
        if (s.running) {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_running)
            views.setTextColor(R.id.widget_time, Color.WHITE)
            views.setTextColor(R.id.widget_label, Color.parseColor("#FFD7D2"))
            views.setInt(R.id.widget_tomato, "setColorFilter", Color.parseColor("#FF1744"))
        } else {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg)
            views.setTextColor(R.id.widget_time, Color.parseColor("#2A120E"))
            views.setTextColor(R.id.widget_label, Color.parseColor("#7A4038"))
            views.setInt(R.id.widget_tomato, "setColorFilter", Color.parseColor("#E23B2F"))
        }
        views.setOnClickPendingIntent(R.id.widget_tomato, broadcast(context, TomatoWidgetProvider.ACTION_TOGGLE, 31))
        views.setOnClickPendingIntent(R.id.widget_plus, broadcast(context, TomatoWidgetProvider.ACTION_PLUS, 32))
        views.setOnClickPendingIntent(R.id.widget_minus, broadcast(context, TomatoWidgetProvider.ACTION_MINUS, 33))
        views.setOnClickPendingIntent(R.id.widget_time, broadcast(context, TomatoWidgetProvider.ACTION_TOGGLE, 34))
        manager.updateAppWidget(id, views)
    }

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, TomatoWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
