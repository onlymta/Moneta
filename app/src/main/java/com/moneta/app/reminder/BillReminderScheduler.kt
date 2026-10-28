package com.moneta.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * One alarm a day (about 9 am) that checks the bills list and shows a notification for any unpaid bill whose
 * reminder date is today. Like the daily reminder it is inexact, and is set again after each run and after a restart.
 * The alarm is simply always scheduled; with nothing to remind about, the receiver does nothing.
 */
object BillReminderScheduler {

    private const val REQUEST_CODE = 4108
    private const val HOUR = 9

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now + 1000L) calendar.add(Calendar.DAY_OF_YEAR, 1)
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent(app))
    }

    private fun pendingIntent(app: Context): PendingIntent {
        val intent = Intent(app, BillReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
