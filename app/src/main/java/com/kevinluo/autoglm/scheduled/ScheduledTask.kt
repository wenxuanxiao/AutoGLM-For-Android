package com.kevinluo.autoglm.scheduled

import org.json.JSONArray
import org.json.JSONObject

/**
 * 定时任务实体
 *
 * @property id 唯一标识
 * @property name 任务名称（用户自定义）
 * @property taskDescription 任务描述（执行什么）
 * @property hour 触发小时 (0-23)
 * @property minute 触发分钟 (0-59)
 * @property repeatType 重复类型
 * @property repeatDays 每周重复的星期 [1-7, 1=周一]
 * @property isEnabled 是否启用
 * @property createdAt 创建时间
 * @property lastExecutedAt 上次执行时间
 * @property nextExecuteAt 下次执行时间（缓存）
 */
data class ScheduledTask(
    val id: String,
    val name: String,
    val taskDescription: String,
    val hour: Int,
    val minute: Int,
    val repeatType: RepeatType = RepeatType.ONCE,
    val repeatDays: List<Int> = emptyList(),
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastExecutedAt: Long? = null,
    val nextExecuteAt: Long? = null
) {
    /**
     * 转换为 JSON 字符串用于存储
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("taskDescription", taskDescription)
            put("hour", hour)
            put("minute", minute)
            put("repeatType", repeatType.name)
            put("repeatDays", JSONArray(repeatDays))
            put("isEnabled", isEnabled)
            put("createdAt", createdAt)
            lastExecutedAt?.let { put("lastExecutedAt", it) }
            nextExecuteAt?.let { put("nextExecuteAt", it) }
        }.toString()
    }

    companion object {
        /**
         * 从 JSON 字符串解析
         */
        fun fromJson(json: String): ScheduledTask? {
            return try {
                val obj = JSONObject(json)
                val repeatDaysArray = obj.optJSONArray("repeatDays")
                val repeatDays = if (repeatDaysArray != null) {
                    (0 until repeatDaysArray.length()).map { repeatDaysArray.getInt(it) }
                } else {
                    emptyList()
                }

                ScheduledTask(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    taskDescription = obj.getString("taskDescription"),
                    hour = obj.getInt("hour"),
                    minute = obj.getInt("minute"),
                    repeatType = RepeatType.valueOf(obj.optString("repeatType", "ONCE")),
                    repeatDays = repeatDays,
                    isEnabled = obj.optBoolean("isEnabled", true),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    lastExecutedAt = if (obj.has("lastExecutedAt")) obj.getLong("lastExecutedAt") else null,
                    nextExecuteAt = if (obj.has("nextExecuteAt")) obj.getLong("nextExecuteAt") else null
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 获取友好的重复类型描述
     */
    fun getRepeatTypeDescription(): String {
        return when (repeatType) {
            RepeatType.ONCE -> "仅一次"
            RepeatType.DAILY -> "每天"
            RepeatType.WEEKLY -> {
                val daysText = repeatDays.sorted().joinToString("、") { getWeekdayShortName(it) }
                "每周 $daysText"
            }
        }
    }

    /**
     * 获取时间描述
     */
    fun getTimeDescription(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    /**
     * 获取星期短名称
     */
    private fun getWeekdayShortName(day: Int): String {
        return when (day) {
            1 -> "一"
            2 -> "二"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            6 -> "六"
            7 -> "日"
            else -> ""
        }
    }
}

/**
 * 任务执行结果
 */
data class ScheduledTaskResult(
    val taskId: String,
    val success: Boolean,
    val message: String,
    val executedAt: Long = System.currentTimeMillis()
)

/**
 * 重复类型枚举
 */
enum class RepeatType {
    ONCE,     // 仅一次
    DAILY,    // 每天
    WEEKLY,   // 每周特定天
}
