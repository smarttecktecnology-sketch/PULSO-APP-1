package com.smartteck.orbit
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class OrbitReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Tarefa pendente"
        val taskId = inputData.getLong(KEY_TASK_ID, System.currentTimeMillis())
        val leadMinutes = inputData.getInt(KEY_LEAD_MINUTES, 0)
        createOrbitNotificationChannel(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val openApp = PendingIntent.getActivity(
            applicationContext, taskId.hashCode(),
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Lembrete SMART ORBIT")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (leadMinutes > 0) "Faltam $leadMinutes minutos para: $title" else "Está na hora de: $title"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(taskId.hashCode(), notification)
        return Result.success()
    }
    companion object {
        const val CHANNEL_ID = "orbit_task_reminders"
        const val KEY_TITLE = "task_title"
        const val KEY_TASK_ID = "task_id"
        const val KEY_LEAD_MINUTES = "lead_minutes"
    }
}
fun createOrbitNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            OrbitReminderWorker.CHANNEL_ID,
            "Lembretes de tarefas",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Avisos sobre tarefas com data definida no SMART ORBIT." })
    }
}
