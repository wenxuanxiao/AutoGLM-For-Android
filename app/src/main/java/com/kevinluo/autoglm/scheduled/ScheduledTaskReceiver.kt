package com.kevinluo.autoglm.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kevinluo.autoglm.util.Logger
import dev.rikka.shizuku.Shizuku

/**
 * 定时任务广播接收器
 *
 * 接收 AlarmManager 发出的定时信号，检查任务状态并触发执行
 */
class ScheduledTaskReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduledTaskReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = ScheduledTaskManager.getTaskId(intent)

        if (taskId == null) {
            Logger.e(TAG, "No task ID in intent")
            return
        }

        Logger.d(TAG, "Received alarm for task: $taskId")

        val manager = ScheduledTaskManager.getInstance(context)
        val task = manager.getTaskById(taskId)

        if (task == null) {
            Logger.w(TAG, "Task not found: $taskId")
            return
        }

        if (!task.isEnabled) {
            Logger.d(TAG, "Task is disabled, skipping: ${task.name}")
            return
        }

        if (Shizuku.isBinding()) {
            Logger.d(TAG, "Shizuku is available, executing task: ${task.name}")
            executeTask(context, taskId)
        } else {
            Logger.w(TAG, "Shizuku not available, showing notification")
            manager.showNotification(
                taskId = taskId,
                title = "定时任务等待执行",
                message = "任务「${task.name}」等待 Shizuku 连接"
            )
        }
    }

    private fun executeTask(context: Context, taskId: String) {
        val manager = ScheduledTaskManager.getInstance(context)
        val task = manager.getTaskById(taskId)

        if (task == null) {
            Logger.w(TAG, "Task not found when executing: $taskId")
            return
        }

        manager.showNotification(
            taskId = taskId,
            title = "定时任务执行中",
            message = "正在执行任务「${task.name}」",
            isOngoing = true
        )

        val workRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(workDataOf(ScheduledTaskWorker.KEY_TASK_ID to taskId))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        Logger.d(TAG, "Work request enqueued for task: ${task.name}")
    }
}
