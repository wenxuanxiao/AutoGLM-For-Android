package com.kevinluo.autoglm.scheduled

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.util.Logger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 定时任务管理器
 *
 * 负责定时任务的 CRUD 操作、调度管理、下次执行时间计算等核心逻辑
 *
 * @param context Android 上下文
 */
class ScheduledTaskManager(private val context: Context) {

    companion object {
        private const val TAG = "ScheduledTaskManager"
        private const val CHANNEL_ID = "scheduled_task_channel"
        private const val CHANNEL_NAME = "定时任务"
        private const val EXTRA_TASK_ID = "extra_task_id"

        @Volatile
        private var instance: ScheduledTaskManager? = null

        fun getInstance(context: Context): ScheduledTaskManager {
            return instance ?: synchronized(this) {
                instance ?: ScheduledTaskManager(context.applicationContext).also { instance = it }
            }
        }

        fun getTaskId(intent: Intent): String? {
            return intent.getStringExtra(EXTRA_TASK_ID)
        }
    }

    private val settingsManager = SettingsManager(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "定时任务执行通知"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getAllTasks(): List<ScheduledTask> {
        return settingsManager.getScheduledTasks()
    }

    fun getEnabledTasks(): List<ScheduledTask> {
        return getAllTasks().filter { it.isEnabled }
    }

    fun getTaskById(taskId: String): ScheduledTask? {
        return settingsManager.getScheduledTaskById(taskId)
    }

    fun saveTask(task: ScheduledTask) {
        Logger.d(TAG, "Saving task: ${task.name}")
        val taskWithNextTime = if (task.isEnabled) {
            task.copy(nextExecuteAt = calculateNextExecuteAt(task))
        } else {
            task.copy(nextExecuteAt = null)
        }
        settingsManager.saveScheduledTask(taskWithNextTime)

        if (task.isEnabled) {
            scheduleTask(taskWithNextTime)
        } else {
            cancelTask(task)
        }
    }

    fun deleteTask(taskId: String) {
        Logger.d(TAG, "Deleting task: $taskId")
        val task = getTaskById(taskId)
        if (task != null) {
            cancelTask(task)
        }
        settingsManager.deleteScheduledTask(taskId)
    }

    fun updateTaskEnabled(taskId: String, enabled: Boolean) {
        val task = getTaskById(taskId) ?: return
        saveTask(task.copy(isEnabled = enabled))
    }

    fun updateLastExecuted(taskId: String) {
        val task = getTaskById(taskId) ?: return
        val now = System.currentTimeMillis()
        val updatedTask = task.copy(
            lastExecutedAt = now,
            nextExecuteAt = calculateNextExecuteAt(task.copy(lastExecutedAt = now))
        )
        settingsManager.saveScheduledTask(updatedTask)

        if (updatedTask.isEnabled) {
            scheduleTask(updatedTask)
        }
    }

    fun scheduleAllTasks() {
        Logger.d(TAG, "Scheduling all enabled tasks")
        getEnabledTasks().forEach { task ->
            scheduleTask(task)
        }
    }

    fun cancelAllTasks() {
        Logger.d(TAG, "Cancelling all tasks")
        getAllTasks().forEach { task ->
            cancelTask(task)
        }
    }

    private fun scheduleTask(task: ScheduledTask) {
        val nextExecuteAt = task.nextExecuteAt ?: calculateNextExecuteAt(task) ?: return

        if (nextExecuteAt <= System.currentTimeMillis()) {
            Logger.w(TAG, "Next execution time is in the past, skipping: ${task.name}")
            return
        }

        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextExecuteAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextExecuteAt,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextExecuteAt,
                    pendingIntent
                )
            }
            Logger.d(TAG, "Scheduled task: ${task.name} at ${formatTime(nextExecuteAt)}")
        } catch (e: SecurityException) {
            Logger.e(TAG, "Failed to schedule exact alarm, using inexact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextExecuteAt,
                pendingIntent
            )
        }
    }

    private fun cancelTask(task: ScheduledTask) {
        val intent = Intent(context, ScheduledTaskReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        Logger.d(TAG, "Cancelled task: ${task.name}")
    }

    fun calculateNextExecuteAt(task: ScheduledTask, fromTime: Long = System.currentTimeMillis()): Long? {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = fromTime
            set(Calendar.HOUR_OF_DAY, task.hour)
            set(Calendar.MINUTE, task.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (task.repeatType) {
            RepeatType.ONCE -> {
                if (calendar.timeInMillis > fromTime) {
                    calendar.timeInMillis
                } else {
                    null
                }
            }
            RepeatType.DAILY -> {
                while (calendar.timeInMillis <= fromTime) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                calendar.timeInMillis
            }
            RepeatType.WEEKLY -> {
                while (calendar.timeInMillis <= fromTime) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val androidDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
                    if (androidDay in task.repeatDays) {
                        break
                    }
                }
                while (calendar.get(Calendar.DAY_OF_WEEK) !in listOf(
                        if (task.repeatDays.contains(1)) Calendar.SUNDAY else -1,
                        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
                    ) || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && !task.repeatDays.contains(7)
                ) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val androidDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
                    if (androidDay in task.repeatDays) {
                        break
                    }
                }

                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val androidDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
                if (androidDay in task.repeatDays) {
                    calendar.timeInMillis
                } else {
                    null
                }
            }
        }
    }

    fun getNextExecuteDescription(task: ScheduledTask): String {
        val nextAt = task.nextExecuteAt ?: calculateNextExecuteAt(task) ?: return "已过期"
        val now = System.currentTimeMillis()

        val dateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
        val timeStr = dateFormat.format(Date(nextAt))

        val diff = nextAt - now
        val days = diff / (24 * 60 * 60 * 1000)

        return when {
            days <= 0 -> "今天 $timeStr"
            days == 1L -> "明天 $timeStr"
            days < 7 -> "${days.toInt()}天后 $timeStr"
            else -> timeStr
        }
    }

    private fun formatTime(time: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return format.format(Date(time))
    }

    fun showNotification(taskId: String, title: String, message: String, isOngoing: Boolean = false) {
        val task = getTaskById(taskId)
        if (task == null) {
            Logger.w(TAG, "Task not found for notification: $taskId")
            return
        }

        val intent = Intent(context, com.kevinluo.autoglm.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(isOngoing)
            .build()

        notificationManager.notify(taskId.hashCode(), notification)
    }

    fun cancelNotification(taskId: String) {
        notificationManager.cancel(taskId.hashCode())
    }

    fun generateNewTaskId(): String {
        return settingsManager.generateScheduledTaskId()
    }

    fun isTaskNameExists(name: String, excludeTaskId: String? = null): Boolean {
        return getAllTasks().any { it.name == name && it.id != excludeTaskId }
    }
}
