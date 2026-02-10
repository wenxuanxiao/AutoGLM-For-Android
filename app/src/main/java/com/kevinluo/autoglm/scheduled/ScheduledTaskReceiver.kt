package com.kevinluo.autoglm.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kevinluo.autoglm.util.Logger
import rikka.shizuku.Shizuku

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
            Logger.d(TAG, "Task is disabled")
            return
        }

        if (Shizuku.pingBinder()) {
            Logger.d(TAG, "Shizuku available, executing task")
            executeTask(context, taskId)
        } else {
            Logger.w(TAG, "Shizuku not available")
            manager.showNotification(taskId, "等待执行", "等待 Shizuku 连接")
        }
    }

    private fun executeTask(context: Context, taskId: String) {
        val manager = ScheduledTaskManager.getInstance(context)
        val task = manager.getTaskById(taskId)

        if (task == null) return

        manager.showNotification(taskId, "执行中", "正在执行任务")

        val serviceIntent = Intent(context, ScheduledTaskWorker::class.java).apply {
            putExtra(ScheduledTaskWorker.KEY_TASK_ID, taskId)
        }
        context.startService(serviceIntent)

        Logger.d(TAG, "Service started for task: ${task.name}")
    }
}
