package com.smartteck.orbit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

internal fun orbitVoiceReminderAllowed(
    isTestMode: Boolean,
    savedPlan: String?,
    voiceSettingEnabled: Boolean,
    reminderVoiceEnabled: Boolean
): Boolean {
    val eligiblePlan = isTestMode ||
        savedPlan == OrbitPlanTier.PLUS.name ||
        savedPlan == OrbitPlanTier.MAX.name
    return reminderVoiceEnabled && voiceSettingEnabled && eligiblePlan
}

class OrbitReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Tarefa pendente"
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId < 0L || !isTaskStillPending(taskId)) return Result.success()

        createOrbitNotificationChannel(applicationContext)
        val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (notificationsAllowed) {
            saveOrbitNotificationHistory(applicationContext, title, when {
                inputData.getBoolean(KEY_LEAD_ALREADY_PASSED, false) -> "Aviso antecipado"
                inputData.getInt(KEY_LEAD_MINUTES, 0) > 0 -> "Lembrete antecipado"
                else -> "Lembrete"
            })
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
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    when {
                        inputData.getBoolean(KEY_LEAD_ALREADY_PASSED, false) ->
                            "O horário do aviso antecipado já passou. Seu compromisso está próximo: $title"
                        inputData.getInt(KEY_LEAD_MINUTES, 0) > 0 ->
                            "Faltam ${inputData.getInt(KEY_LEAD_MINUTES, 0)} minutos para: $title"
                        else -> "Está na hora de: $title"
                    }
                ))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            try {
                NotificationManagerCompat.from(applicationContext).notify(taskId.hashCode(), notification)
            } catch (_: SecurityException) {
                // Permission may have been revoked between the check and notify().
            }
        }

        if (isVoiceReminderAllowed()) {
            val leadMinutes = inputData.getInt(KEY_LEAD_MINUTES, 0)
            speakReminder(
                when {
                    inputData.getBoolean(KEY_LEAD_ALREADY_PASSED, false) ->
                        "Atenção! O aviso antecipado de $title já passou. Seu compromisso está próximo."
                    leadMinutes > 0 -> "Atenção! $title começa daqui a $leadMinutes minutos."
                    else -> "Atenção! Está na hora de $title."
                }
            )
        }
        return Result.success()
    }

    private fun isTaskStillPending(taskId: Long): Boolean = try {
        val json = applicationContext.getSharedPreferences("orbit_data", Context.MODE_PRIVATE)
            .getString("tasks", "[]") ?: "[]"
        val tasks = JSONArray(json)
        (0 until tasks.length()).any { index ->
            val task = tasks.optJSONObject(index)
            task != null && task.optLong("id", -1L) == taskId && !task.optBoolean("done", false)
        }
    } catch (_: Exception) {
        false
    }

    private fun isVoiceReminderAllowed(): Boolean {
        val settings = applicationContext.getSharedPreferences("orbit_settings", Context.MODE_PRIVATE)
        return orbitVoiceReminderAllowed(
            isTestMode = BuildConfig.ORBIT_TEST_MODE,
            savedPlan = settings.getString("active_plan", OrbitPlanTier.FREE.name),
            voiceSettingEnabled = settings.getBoolean("voice_reminders", false),
            reminderVoiceEnabled = inputData.getBoolean(KEY_VOICE_ENABLED, false)
        )
    }

    private fun speakReminder(message: String) {
        val initialized = CountDownLatch(1)
        var engine: TextToSpeech? = null
        try {
            engine = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    engine?.language = Locale("pt", "BR")
                    engine?.setSpeechRate(0.95f)
                }
                initialized.countDown()
            }
            if (!initialized.await(8, TimeUnit.SECONDS)) return
            val tts = engine ?: return
            if (tts.isLanguageAvailable(Locale("pt", "BR")) < TextToSpeech.LANG_AVAILABLE) return
            val finished = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { finished.countDown() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { finished.countDown() }
            })
            val result = tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "orbit_voice_reminder")
            if (result == TextToSpeech.ERROR) return
            finished.await(18, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // The visual notification remains available if speech synthesis fails.
        } finally {
            engine?.shutdown()
        }
    }

    companion object {
        const val CHANNEL_ID = "orbit_task_reminders"
        const val KEY_TITLE = "task_title"
        const val KEY_TASK_ID = "task_id"
        const val KEY_LEAD_MINUTES = "lead_minutes"
        const val KEY_VOICE_ENABLED = "voice_enabled"
        const val KEY_LEAD_ALREADY_PASSED = "lead_already_passed"
    }
}

internal fun saveOrbitNotificationHistory(context: Context, title: String, kind: String, timestamp: Long = System.currentTimeMillis()) {
    val prefs = context.getSharedPreferences("orbit_data", Context.MODE_PRIVATE)
    val history = try { JSONArray(prefs.getString("notification_history", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
    history.put(org.json.JSONObject().put("id", timestamp).put("title", title.take(180)).put("kind", kind.take(40)).put("timestamp", timestamp))
    val trimmed = JSONArray()
    val start = (history.length() - 100).coerceAtLeast(0)
    for (index in start until history.length()) trimmed.put(history.getJSONObject(index))
    prefs.edit().putString("notification_history", trimmed.toString()).apply()
}

fun createOrbitNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            OrbitReminderWorker.CHANNEL_ID,
            "Lembretes de tarefas",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos sobre tarefas com data definida no SMART ORBIT."
        })
    }
}
