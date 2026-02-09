package com.kevinluo.autoglm.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kevinluo.autoglm.util.Logger
import dev.rikka.shizuku.Shizuku

class ScheduledTaskReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduledTaskReceiver"
        const val ACTION_TASK_TRIGGER = "com.kevinluo.autoglm.scheduled.TASK_TRIGGER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ScheduledTaskWorker.KEY_TASK_ID)

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
                message = "等待 Shizuku 连接"
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
            message = "正在执行任务"
        )

        val serviceIntent = Intent(context, ScheduledTaskWorker::class.java).apply {
            putExtra(ScheduledTaskWorker.KEY_TASK_ID, taskId)
        }
        context.startService(serviceIntent)

        Logger.d(TAG, "Service started for task: ${task.name}")
    }
}
