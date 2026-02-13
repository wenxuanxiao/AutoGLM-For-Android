package com.kevinluo.autoglm.scheduled

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.kevinluo.autoglm.BuildConfig
import com.kevinluo.autoglm.IUserService
import com.kevinluo.autoglm.UserService
import com.kevinluo.autoglm.action.AgentAction
import com.kevinluo.autoglm.agent.PhoneAgent
import com.kevinluo.autoglm.agent.PhoneAgentListener
import com.kevinluo.autoglm.agent.TaskResult
import com.kevinluo.autoglm.app.AppResolver
import com.kevinluo.autoglm.history.HistoryManager
import com.kevinluo.autoglm.input.TextInputManager
import com.kevinluo.autoglm.model.ModelClient
import com.kevinluo.autoglm.screenshot.ScreenshotService
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.ui.FloatingWindowService
import com.kevinluo.autoglm.ui.TaskStatus
import com.kevinluo.autoglm.util.HumanizedSwipeGenerator
import com.kevinluo.autoglm.util.Logger
import rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScheduledTaskWorker : Service(), PhoneAgentListener {

    companion object {
        private const val TAG = "ScheduledTaskWorker"
        const val KEY_TASK_ID = "key_task_id"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = false
    private var currentStepNumber = 0
    private var currentThinking = ""
    private var floatingService: FloatingWindowService? = null
    private var phoneAgent: PhoneAgent? = null
    private var isCancelled = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            BuildConfig.APPLICATION_ID,
            UserService::class.java.name
        )
    )
        .daemon(false)
        .processNameSuffix("scheduled_task_worker")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private var userService: IUserService? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(KEY_TASK_ID) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        Logger.d(TAG, "Starting scheduled task: $taskId")

        if (isRunning) {
            Logger.w(TAG, "Task already running")
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true

        serviceScope.launch {
            executeTask(taskId)
        }

        return START_NOT_STICKY
    }

    private suspend fun executeTask(taskId: String) {
        val manager = ScheduledTaskManager.getInstance(this)
        val task = manager.getTaskById(taskId)

        if (task == null) {
            Logger.e(TAG, "Task not found: $taskId")
            manager.showNotification(taskId, "执行失败", "任务未找到")
            manager.updateLastExecuted(taskId)
            return
        }

        if (!Shizuku.pingBinder()) {
            Logger.w(TAG, "Shizuku not available")
            manager.showNotification(taskId, "等待执行", "等待 Shizuku 连接")
            manager.updateLastExecuted(taskId)
            return
        }

        try {
            // 启动浮窗服务
            Logger.d(TAG, "Starting floating window service for scheduled task")
            val intent = Intent(this, FloatingWindowService::class.java)
            startService(intent)

            // 等待浮窗服务初始化
            var waitCount = 0
            floatingService = FloatingWindowService.getInstance()
            while (floatingService == null && waitCount < 20) {
                kotlinx.coroutines.delay(100)
                waitCount++
                floatingService = FloatingWindowService.getInstance()
            }

            if (floatingService != null) {
                Logger.d(TAG, "Floating window service initialized after ${waitCount * 100}ms")

                val service = floatingService!!

                // 设置停止回调
                service.setStopTaskCallback {
                    Logger.d(TAG, "Stop callback invoked")
                    isCancelled = true
                    phoneAgent?.cancel()
                }

                // 设置暂停回调
                service.setPauseTaskCallback {
                    Logger.d(TAG, "Pause callback invoked")
                    phoneAgent?.pause()
                }

                currentStepNumber = 0
                currentThinking = ""
                service.updateStatus(TaskStatus.RUNNING)
                service.updateStepNumber(0)
                service.show()
            } else {
                Logger.e(TAG, "Floating window service failed to initialize")
            }

            val result = doExecuteTask(task)

            if (result.success) {
                Logger.i(TAG, "Task executed successfully")
                manager.updateLastExecuted(taskId)
                manager.cancelNotification(taskId)
                FloatingWindowService.getInstance()?.showResult("任务已完成", true)
            } else {
                Logger.e(TAG, "Task execution failed: ${result.message}")
                manager.updateLastExecuted(taskId)
                manager.cancelNotification(taskId)
                FloatingWindowService.getInstance()?.showResult(result.message, false)
            }

        } catch (e: kotlinx.coroutines.CancellationException) {
            Logger.w(TAG, "Task execution cancelled")
            try {
                manager.updateLastExecuted(taskId)
                FloatingWindowService.getInstance()?.showResult("任务已取消", false)
            } catch (ignore: Exception) {
            }
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Task execution error: ${e.message}")
            try {
                manager.updateLastExecuted(taskId)
                manager.cancelNotification(taskId)
                FloatingWindowService.getInstance()?.showResult(e.message ?: "未知错误", false)
            } catch (ignore: Exception) {
            }
        } finally {
            this.phoneAgent = null
            isRunning = false
            stopSelf()
        }
    }

    private suspend fun doExecuteTask(task: ScheduledTask): TaskResult {
        return try {
            val settingsManager = SettingsManager(this)
            val modelConfig = settingsManager.getModelConfig()
            val agentConfig = settingsManager.getAgentConfig()

            if (modelConfig.apiKey == "EMPTY" || modelConfig.baseUrl.isEmpty()) {
                return TaskResult(success = false, message = "API 配置未完成", stepCount = 0)
            }

            val modelClient = ModelClient(modelConfig)
            val historyManager = HistoryManager.getInstance(this)
            val appResolver = AppResolver(this.packageManager)
            val swipeGenerator = HumanizedSwipeGenerator()

            val service = bindUserService()

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

            val phoneAgent = PhoneAgent(
                modelClient = modelClient,
                actionHandler = actionHandler,
                screenshotService = screenshotService,
                config = agentConfig,
                historyManager = historyManager
            )
            this.phoneAgent = phoneAgent
            phoneAgent.setListener(this)

            phoneAgent.run(task.taskDescription)

        } catch (e: kotlinx.coroutines.CancellationException) {
            Logger.w(TAG, "Task execution cancelled")
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Error executing task: ${e.message}")
            TaskResult(success = false, message = e.message ?: "未知错误", stepCount = 0)
        } finally {
            unbindUserService()
        }
    }

    private fun unbindUserService() {
        try {
            Shizuku.unbindUserService(userServiceArgs, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {}
                override fun onServiceDisconnected(name: ComponentName?) {}
            }, true)
            Logger.d(TAG, "UserService unbound")
        } catch (e: Exception) {
            Logger.e(TAG, "Error unbinding service: ${e.message}")
        }
    }

    private suspend fun bindUserService(): IUserService {
        return suspendCancellableCoroutine { continuation ->
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val service = IUserService.Stub.asInterface(binder)
                    userService = service
                    Logger.d(TAG, "UserService connected")
                    if (continuation.isActive) {
                        continuation.resume(service)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    Logger.d(TAG, "UserService disconnected")
                    userService = null
                }
            }

            try {
                Logger.d(TAG, "Binding user service...")
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to bind user service", e)
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            continuation.invokeOnCancellation {
                Logger.d(TAG, "Cancelling user service binding")
                try {
                    Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
                } catch (e: Exception) {
                    Logger.e(TAG, "Error unbinding service", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Logger.d(TAG, "Service destroyed")
    }

    // PhoneAgentListener callbacks for floating window updates
    override fun onStepStarted(stepNumber: Int) {
        currentStepNumber = stepNumber
        currentThinking = ""
        floatingService?.let { service ->
            service.updateStepNumber(stepNumber)
            service.addStep(stepNumber, "", null)
            Logger.d(TAG, "Step started: $stepNumber")
        }
    }

    override fun onThinkingUpdate(thinking: String) {
        currentThinking = thinking
        floatingService?.let { service ->
            service.updateThinking(thinking)
            // 更新最后一个步骤的思考内容
            Logger.d(TAG, "Thinking update: ${thinking.take(100)}")
        }
    }

    override fun onActionExecuted(action: AgentAction) {
        floatingService?.let { service ->
            // 更新最后一个步骤显示执行的动作
            service.addStep(currentStepNumber, currentThinking, action)
            Logger.d(TAG, "Action executed: ${action.formatForDisplay()}")
        }
    }

    override fun onTaskCompleted(message: String) {
        floatingService?.let { service ->
            service.showResult("任务已完成: $message", true)
            Logger.d(TAG, "Task completed: $message")
        }
    }

    override fun onTaskFailed(error: String) {
        floatingService?.let { service ->
            service.showResult("任务失败: $error", false)
            Logger.d(TAG, "Task failed: $error")
        }
    }

    override fun onScreenshotStarted() {
        Logger.d(TAG, "Screenshot started")
    }

    override fun onScreenshotCompleted() {
        Logger.d(TAG, "Screenshot completed")
    }

    override fun onFloatingWindowRefreshNeeded() {
        floatingService?.let { service ->
            Logger.d(TAG, "Floating window refresh needed")
        }
    }
}
