package com.kevinluo.autoglm.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kevinluo.autoglm.util.Logger

/**
 * 设备启动完成接收器
 *
 * 设备重启后重新调度所有已启用的定时任务
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Logger.i(TAG, "Boot completed, rescheduling tasks")

        val manager = ScheduledTaskManager.getInstance(context)
        manager.scheduleAllTasks()

        Logger.i(TAG, "All tasks rescheduled")
    }
}
