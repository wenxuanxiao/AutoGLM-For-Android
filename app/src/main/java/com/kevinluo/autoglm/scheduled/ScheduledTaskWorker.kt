package com.kevinluo.autoglm.scheduled

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kevinluo.autoglm.ComponentManager
import com.kevinluo.autoglm.agent.AgentConfig
import com.kevinluo.autoglm.agent.PhoneAgent
import com.kevinluo.autoglm.device.DeviceExecutor
import com.kevinluo.autoglm.history.HistoryManager
import com.kevinluo.autoglm.input.TextInputManager
import com.kevinluo.autoglm.model.ModelClient
import com.kevinluo.autoglm.screenshot.ScreenshotService
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.ui.FloatingWindowService
import com.kevinluo.autoglm.util.HumanizedSwipeGenerator
import com.kevinluo.autoglm.util.Logger
import dev.rikka.shizuku.Shizuku

/**
 * 定时任务执行 Worker
 *
 * 使用 WorkManager 在后台执行定时任务
 */
class ScheduledTaskWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ScheduledTaskWorker"
        const val KEY_TASK_ID = "key_task_id"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()

        Logger.d(TAG, "Starting scheduled task: $taskId")

        val manager = ScheduledTaskManager.getInstance(applicationContext)
        val task = manager.getTaskById(taskId)

        if (task == null) {
            Logger.e(TAG, "Task not found: $taskId")
            manager.showNotification(
                taskId = taskId,
                title = "定时任务执行失败",
                message = "任务未找到"
            )
            return Result.failure()
        }

        try {
            if (!Shizuku.isBinding()) {
                Logger.w(TAG, "Shizuku not available")
                manager.showNotification(
                    taskId = taskId,
                    title = "定时任务等待执行",
                    message = "任务「${task.name}」等待 Shizuku 连接"
                )
                return Result.retry()
            }

            val result = executeTask(task)

            if (result.success) {
                Logger.i(TAG, "Task executed successfully: ${task.name}")
                manager.updateLastExecuted(taskId)
                manager.cancelNotification(taskId)
                manager.showNotification(
                    taskId = taskId,
                    title = "定时任务执行成功",
                    message = "任务「${task.name}」已完成"
                )
            } else {
                Logger.e(TAG, "Task execution failed: ${task.name}, message: ${result.message}")
                manager.cancelNotification(taskId)
                manager.showNotification(
                    taskId = taskId,
                    title = "定时任务执行失败",
                    message = "任务「${task.name}」失败: ${result.message}"
                )
            }

            return if (result.success) Result.success() else Result.failure()

        } catch (e: Exception) {
            Logger.e(TAG, "Task execution error: ${task.name}", e)
            manager.cancelNotification(taskId)
            manager.showNotification(
                taskId = taskId,
                title = "定时任务执行出错",
                message = "任务「${task.name}」出错: ${e.message}"
            )
            return Result.failure()
        }
    }

    private suspend fun executeTask(task: ScheduledTask): com.kevinluo.autoglm.agent.TaskResult {
        return try {
            val settingsManager = SettingsManager(applicationContext)
            val modelConfig = settingsManager.getModelConfig()
            val agentConfig = settingsManager.getAgentConfig()

            if (modelConfig.apiKey == "EMPTY" || modelConfig.baseUrl.isEmpty()) {
                return com.kevinluo.autoglm.agent.TaskResult(
                    success = false,
                    message = "API 配置未完成",
                    stepCount = 0
                )
            }

            val modelClient = ModelClient(modelConfig)
            val historyManager = HistoryManager.getInstance(applicationContext)
            val appResolver = com.kevinluo.autoglm.app.AppResolver(applicationContext.packageManager)
            val swipeGenerator = HumanizedSwipeGenerator()

            val service = Shizuku.newUserServiceBuilder()
                .build()

            val textInputManager = TextInputManager(service)
            val deviceExecutor = DeviceExecutor(service)
            val screenshotService = ScreenshotService(service) {
                FloatingWindowService.getInstance()
            }

            val actionHandler = com.kevinluo.autoglm.action.ActionHandler(
                deviceExecutor = deviceExecutor,
                appResolver = appResolver,
                swipeGenerator = swipeGenerator,
                textInputManager = textInputManager,
                floatingWindowProvider = { FloatingWindowService.getInstance() }
            )

            val phoneAgent = PhoneAgent(
                modelClient = modelClient,
                actionHandler = actionHandler,
                screenshotService = screenshotService,
                config = agentConfig,
                historyManager = historyManager
            )

            phoneAgent.run(task.taskDescription)

        } catch (e: Shizuku.ServiceNotConnectedException) {
            Logger.e(TAG, "Shizuku service not connected", e)
            com.kevinluo.autoglm.agent.TaskResult(
                success = false,
                message = "Shizuku 服务未连接",
                stepCount = 0
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Error executing task", e)
            com.kevinluo.autoglm.agent.TaskResult(
                success = false,
                message = e.message ?: "未知错误",
                stepCount = 0
            )
        }
    }
}
