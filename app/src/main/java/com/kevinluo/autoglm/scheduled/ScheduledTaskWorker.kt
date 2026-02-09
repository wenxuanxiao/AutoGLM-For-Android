package com.kevinluo.autoglm.scheduled

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kevinluo.autoglm.agent.TaskResult
import com.kevinluo.autoglm.app.AppResolver
import com.kevinluo.autoglm.history.HistoryManager
import com.kevinluo.autoglm.input.TextInputManager
import com.kevinluo.autoglm.model.ModelClient
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.ui.FloatingWindowService
import com.kevinluo.autoglm.util.HumanizedSwipeGenerator
import com.kevinluo.autoglm.util.Logger
import dev.rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScheduledTaskWorker : Service() {

    companion object {
        private const val TAG = "ScheduledTaskWorker"
        const val KEY_TASK_ID = "key_task_id"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(KEY_TASK_ID)

        if (taskId == null) {
            Logger.e(TAG, "No task ID in intent")
            stopSelf()
            return START_NOT_STICKY
        }

        Logger.d(TAG, "Starting scheduled task: $taskId")

        if (isRunning) {
            Logger.w(TAG, "Task already running, ignoring duplicate")
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true

        serviceScope.launch {
            executeTask(taskId)
            isRunning = false
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private suspend fun executeTask(taskId: String) {
        val manager = ScheduledTaskManager.getInstance(this)
        val task = manager.getTaskById(taskId)

        if (task == null) {
            Logger.e(TAG, "Task not found: $taskId")
            manager.showNotification(
                taskId = taskId,
                title = "定时任务执行失败",
                message = "任务未找到"
            )
            return
        }

        try {
            if (!Shizuku.isBinding()) {
                Logger.w(TAG, "Shizuku not available")
                manager.showNotification(
                    taskId = taskId,
                    title = "定时任务等待执行",
                    message = "任务等待 Shizuku 连接"
                )
                return
            }

            val result = doExecuteTask(task)

            if (result.success) {
                Logger.i(TAG, "Task executed successfully: ${task.name}")
                manager.updateLastExecuted(taskId)
                manager.cancelNotification(taskId)
                manager.showNotification(
                    taskId = taskId,
                    title = "定时任务执行成功",
                    message = "任务已完成"
                )
            } else {
                Logger.e(TAG, "Task execution failed: ${result.message}")
                manager.cancelNotification(taskId)
                manager.showNotification(
                    taskId = taskId,
                    title = "定时任务执行失败",
                    message = result.message
                )
            }

        } catch (e: Exception) {
            Logger.e(TAG, "Task execution error: ${e.message}")
            manager.cancelNotification(taskId)
            manager.showNotification(
                taskId = taskId,
                title = "定时任务执行出错",
                message = e.message ?: "未知错误"
            )
        }
    }

    private suspend fun doExecuteTask(task: ScheduledTask): TaskResult {
        return try {
            val settingsManager = SettingsManager(this)
            val modelConfig = settingsManager.getModelConfig()
            val agentConfig = settingsManager.getAgentConfig()

            if (modelConfig.apiKey == "EMPTY" || modelConfig.baseUrl.isEmpty()) {
                return TaskResult(
                    success = false,
                    message = "API 配置未完成",
                    stepCount = 0
                )
            }

            val modelClient = ModelClient(modelConfig)
            val historyManager = HistoryManager.getInstance(this)
            val appResolver = AppResolver(this.packageManager)
            val swipeGenerator = HumanizedSwipeGenerator()

            val service = Shizuku.newUserServiceBuilder()
                .build()

            val textInputManager = TextInputManager(service)
            val deviceExecutor = com.kevinluo.autoglm.device.DeviceExecutor(service)
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

            val phoneAgent = com.kevinluo.autoglm.agent.PhoneAgent(
                modelClient = modelClient,
                actionHandler = actionHandler,
                screenshotService = screenshotService,
                config = agentConfig,
                historyManager = historyManager
            )

            phoneAgent.run(task.taskDescription)

        } catch (e: Shizuku.ServiceNotConnectedException) {
            Logger.e(TAG, "Shizuku service not connected")
            TaskResult(
                success = false,
                message = "Shizuku 服务未连接",
                stepCount = 0
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Error executing task: ${e.message}")
            TaskResult(
                success = false,
                message = e.message ?: "未知错误",
                stepCount = 0
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Logger.d(TAG, "Service destroyed")
    }
}
