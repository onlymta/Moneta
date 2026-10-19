package com.moneta.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules the daily "record your transactions" reminder with the system alarm service.
 * The alarm is inexact (the phone may deliver it a little late to save battery) and is set
 * again after every reminder and after the phone restarts.
 */
object ReminderScheduler {

    private const val PREFS = "moneta_reminder"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val REQUEST_CODE = 4107

    /** Sets (or replaces) the daily reminder for [hour]:[minute]. */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
        scheduleNext(app, hour, minute)
    }

    /** Turns the reminder off. */
    fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(app))
    }

    /** Sets the alarm again from the saved time; used after the reminder fires and after a restart. */
    fun restore(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ENABLED, false)) {
            scheduleNext(app, prefs.getInt(KEY_HOUR, 21), prefs.getInt(KEY_MINUTE, 0))
        }
    }

    private fun scheduleNext(app: Context, hour: Int, minute: Int) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now + 1000L) calendar.add(Calendar.DAY_OF_YEAR, 1)
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent(app))
    }

    private fun pendingIntent(app: Context): PendingIntent {
        val intent = Intent(app, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
