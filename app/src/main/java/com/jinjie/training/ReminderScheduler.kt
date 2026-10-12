package com.jinjie.training

import android.Manifest
import android.app.AlarmManager
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
import java.util.Calendar

object ReminderScheduler {
    fun configure(context: Context, enabled: Boolean) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, 107, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.cancel(pending)
        if (enabled) {
            val whenToFire = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
            }
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, whenToFire.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!context.getSharedPreferences("training_settings", Context.MODE_PRIVATE).getBoolean("reminders_enabled", false)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("training_reminder", "训练打卡提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val openApp = PendingIntent.getActivity(context, 108, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "training_reminder")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("进阶训练")
            .setContentText("看看今天的安排，记得完成训练或恢复日打卡。")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(107, notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val enabled = context.getSharedPreferences("training_settings", Context.MODE_PRIVATE).getBoolean("reminders_enabled", false)
            ReminderScheduler.configure(context, enabled)
        }
    }
}
