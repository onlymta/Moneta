package com.moneta.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Sets the reminder alarm again after the phone restarts (alarms are cleared on reboot). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.restore(context)
        }
    }
}
