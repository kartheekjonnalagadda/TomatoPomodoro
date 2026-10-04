package com.tomatopomodoro.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PomodoroState(
    val durationMinutes: Int = 25,
    val remainingMillis: Long = 25 * 60_000L,
    val running: Boolean = false,
    val endWallMs: Long = 0L,
    val ringtoneUri: String? = null,
)

object PomodoroEngine {
    val steps = (1..11).map { it * 5 }

    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state.asStateFlow()

    private var tone: ToneGenerator? = null

    fun load(context: Context) {
        val p = prefs(context)
        val minutes = p.getInt(KEY_MINUTES, 25).coerceIn(5, 55)
        val running = p.getBoolean(KEY_RUNNING, false)
        val end = p.getLong(KEY_END, 0L)
        val savedRemaining = p.getLong(KEY_REMAINING, minutes * 60_000L)
        val uri = p.getString(KEY_URI, null)
        val now = System.currentTimeMillis()
        val remaining = when {
            running && end > now -> end - now
            running -> 0L
            else -> savedRemaining.coerceAtLeast(0L)
        }
        _state.value = PomodoroState(
            durationMinutes = minutes,
            remainingMillis = remaining,
            running = running && remaining > 0L,
            endWallMs = if (running) end else 0L,
            ringtoneUri = uri,
        )
        if (running && remaining <= 0L) {
            complete(context, fromLoad = true)
        }
    }

    fun selectMinutes(context: Context, minutes: Int, feedback: Boolean) {
        val snapped = steps.minBy { kotlin.math.abs(it - minutes) }
        val current = _state.value
        if (current.running) return
        if (snapped == current.durationMinutes && current.remainingMillis == snapped * 60_000L) return
        _state.value = current.copy(
            durationMinutes = snapped,
            remainingMillis = snapped * 60_000L,
            running = false,
            endWallMs = 0L,
        )
        persist(context)
        if (feedback) tick(context)
        WidgetUpdater.refresh(context)
    }

    fun nudge(context: Context, deltaMinutes: Int) {
        val current = _state.value
        if (current.running) {
            val next = (currentRemaining() + deltaMinutes * 60_000L).coerceIn(5_000L, 55 * 60_000L)
            val end = System.currentTimeMillis() + next
            _state.value = current.copy(remainingMillis = next, endWallMs = end)
            persist(context)
            AlarmScheduler.schedule(context, end)
            WidgetUpdater.refresh(context)
            context.startService(Intent(context, TimerService::class.java))
        } else {
            val next = (current.durationMinutes + deltaMinutes).coerceIn(5, 55)
            val snapped = steps.minBy { kotlin.math.abs(it - next) }
            selectMinutes(context, snapped, feedback = true)
        }
    }

    fun toggle(context: Context) {
        if (_state.value.running) pause(context) else start(context)
    }

    fun start(context: Context) {
        val current = _state.value
        val remaining = if (current.remainingMillis <= 0L) {
            current.durationMinutes * 60_000L
        } else current.remainingMillis
        val end = System.currentTimeMillis() + remaining
        _state.value = current.copy(remainingMillis = remaining, running = true, endWallMs = end)
        persist(context)
        AlarmScheduler.schedule(context, end)
        ContextCompatStart.startTimer(context)
        WidgetUpdater.refresh(context)
    }

    fun pause(context: Context) {
        val left = currentRemaining()
        _state.value = _state.value.copy(running = false, remainingMillis = left, endWallMs = 0L)
        persist(context)
        AlarmScheduler.cancel(context)
        context.stopService(Intent(context, TimerService::class.java))
        WidgetUpdater.refresh(context)
    }

    fun reset(context: Context) {
        val minutes = _state.value.durationMinutes
        _state.value = _state.value.copy(
            running = false,
            remainingMillis = minutes * 60_000L,
            endWallMs = 0L,
        )
        persist(context)
        AlarmScheduler.cancel(context)
        context.stopService(Intent(context, TimerService::class.java))
        WidgetUpdater.refresh(context)
    }

    fun complete(context: Context, fromLoad: Boolean = false) {
        val minutes = _state.value.durationMinutes
        _state.value = _state.value.copy(
            running = false,
            remainingMillis = minutes * 60_000L,
            endWallMs = 0L,
        )
        persist(context)
        AlarmScheduler.cancel(context)
        context.stopService(Intent(context, TimerService::class.java))
        WidgetUpdater.refresh(context)
        if (!fromLoad) AlarmScheduler.playAlarm(context)
    }

    fun setRingtone(context: Context, uri: String?) {
        _state.value = _state.value.copy(ringtoneUri = uri)
        prefs(context).edit().putString(KEY_URI, uri).apply()
    }

    fun currentRemaining(): Long {
        val s = _state.value
        return if (s.running) (s.endWallMs - System.currentTimeMillis()).coerceAtLeast(0L) else s.remainingMillis
    }

    fun tick(context: Context) {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
        } catch (_: Exception) {
        }
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(12, 40))
        } catch (_: Exception) {
        }
    }

    private fun persist(context: Context) {
        val s = _state.value
        prefs(context).edit()
            .putInt(KEY_MINUTES, s.durationMinutes)
            .putLong(KEY_REMAINING, if (s.running) currentRemaining() else s.remainingMillis)
            .putBoolean(KEY_RUNNING, s.running)
            .putLong(KEY_END, s.endWallMs)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("tomato", Context.MODE_PRIVATE)

    private const val KEY_MINUTES = "minutes"
    private const val KEY_REMAINING = "remaining"
    private const val KEY_RUNNING = "running"
    private const val KEY_END = "end"
    private const val KEY_URI = "ringtone"
}

object AlarmScheduler {
    private var lastAlarmAt = 0L

    fun schedule(context: Context, endWallMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val op = pending(context)
        val show = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endWallMs, op)
            } else {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(endWallMs, show), op)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endWallMs, op)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context))
    }

    fun playAlarm(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastAlarmAt < 4000L) return
        lastAlarmAt = now
        val uri = PomodoroEngine.state.value.ringtoneUri?.let { android.net.Uri.parse(it) }
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        try {
            val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (_: Exception) {
        }
        try {
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 80, 180), -1))
        } catch (_: Exception) {
        }
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction("com.tomatopomodoro.app.ALARM")
        return PendingIntent.getBroadcast(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

object ContextCompatStart {
    fun startTimer(context: Context) {
        val intent = Intent(context, TimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
