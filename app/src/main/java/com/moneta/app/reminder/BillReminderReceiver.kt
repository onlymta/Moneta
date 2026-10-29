package com.moneta.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneta.app.MainActivity
import com.moneta.app.R
import com.moneta.app.data.local.entity.BillStatus
import com.moneta.app.data.repository.Bill
import com.moneta.app.data.repository.BillRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Shows a notification for every unpaid bill whose reminder day (or due day) is today. */
class BillReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BillEntryPoint {
        fun billRepository(): BillRepository
        fun plusRepository(): com.moneta.app.data.plus.PlusRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        // Next day's alarm first, so a failure below can never stop the reminders for good.
        BillReminderScheduler.ensureScheduled(app)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entry = EntryPointAccessors.fromApplication(app, BillEntryPoint::class.java)
                // Bill reminders are a Plus feature, so a Free plan stays quiet.
                if (!entry.plusRepository().state.first().hasPlus) return@launch
                val repository = entry.billRepository()
                val today = LocalDate.now()
                repository.getAll()
                    .filter { it.status == BillStatus.UNPAID && it.reminderDaysBefore >= 0 }
                    .forEach { bill ->
                        val reminderDay = bill.dueDate.minusDays(bill.reminderDaysBefore.toLong())
                        if (reminderDay == today || bill.dueDate == today) notify(app, bill, today)
                    }
            } catch (e: Exception) {
                // A reminder must never crash the app.
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, bill: Bill, today: LocalDate) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Reminders for bills that are due"
            manager.createNotificationChannel(channel)
        }
        val days = ChronoUnit.DAYS.between(today, bill.dueDate)
        val whenText = when {
            days <= 0L -> "is due today"
            days == 1L -> "is due tomorrow"
            else -> "is due in $days days"
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moneta_mark)
            .setContentTitle(bill.name)
            .setContentText("${bill.amount.formatted()} $whenText")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(BASE_ID + (bill.id % 100000).toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "moneta_bills"
        private const val BASE_ID = 810000
    }
}
