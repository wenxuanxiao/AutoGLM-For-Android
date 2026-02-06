package com.kevinluo.autoglm.scheduled

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.settings.TaskTemplate

/**
 * 定时任务编辑对话框
 */
class ScheduledTaskEditDialog(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val task: ScheduledTask?,
    private val onSave: (ScheduledTask) -> Unit
) {

    private val isEdit = task != null
    private lateinit var taskNameLayout: TextInputLayout
    private lateinit var taskDescLayout: TextInputLayout
    private lateinit var taskNameInput: TextInputEditText
    private lateinit var taskDescInput: TextInputEditText
    private lateinit var templateSelector: AutoCompleteTextView
    private lateinit var hourInput: TextInputEditText
    private lateinit var minuteInput: TextInputEditText
    private lateinit var repeatTypeGroup: RadioGroup
    private lateinit var weekdaySelector: LinearLayout
    private lateinit var chipMon: Chip
    private lateinit var chipTue: Chip
    private lateinit var chipWed: Chip
    private lateinit var chipThu: Chip
    private lateinit var chipFri: Chip
    private lateinit var chipSat: Chip
    private lateinit var chipSun: Chip
    private lateinit var enabledSwitch: SwitchMaterial

    private var templates: List<TaskTemplate> = emptyList()

    fun show() {
        templates = settingsManager.getTaskTemplates()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_scheduled_task, null)
        initViews(dialogView)
        prefillIfEdit()
        setupTemplateSelector()
        setupRepeatTypeListener()

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (isEdit) R.string.scheduled_task_edit else R.string.scheduled_task_add)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                if (validateAndSave()) {
                    // Dialog will close automatically
                } else {
                    // Validation failed, recreate dialog
                    show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.show()
    }

    private fun initViews(view: android.view.View) {
        taskNameLayout = view.findViewById(R.id.taskNameLayout)
        taskDescLayout = view.findViewById(R.id.taskDescLayout)
        taskNameInput = view.findViewById(R.id.taskNameInput)
        taskDescInput = view.findViewById(R.id.taskDescInput)
        templateSelector = view.findViewById(R.id.templateSelector)
        hourInput = view.findViewById(R.id.hourInput)
        minuteInput = view.findViewById(R.id.minuteInput)
        repeatTypeGroup = view.findViewById(R.id.repeatTypeGroup)
        weekdaySelector = view.findViewById(R.id.weekdaySelector)
        chipMon = view.findViewById(R.id.chipMon)
        chipTue = view.findViewById(R.id.chipTue)
        chipWed = view.findViewById(R.id.chipWed)
        chipThu = view.findViewById(R.id.chipThu)
        chipFri = view.findViewById(R.id.chipFri)
        chipSat = view.findViewById(R.id.chipSat)
        chipSun = view.findViewById(R.id.chipSun)
        enabledSwitch = view.findViewById(R.id.enabledSwitch)
    }

    private fun prefillIfEdit() {
        if (isEdit && task != null) {
            taskNameInput.setText(task.name)
            taskDescInput.setText(task.taskDescription)
            hourInput.setText(task.hour.toString())
            minuteInput.setText(task.minute.toString())
            enabledSwitch.isChecked = task.isEnabled

            when (task.repeatType) {
                RepeatType.ONCE -> repeatTypeGroup.check(R.id.repeatOnce)
                RepeatType.DAILY -> repeatTypeGroup.check(R.id.repeatDaily)
                RepeatType.WEEKLY -> {
                    repeatTypeGroup.check(R.id.repeatWeekly)
                    weekdaySelector.visibility = LinearLayout.VISIBLE
                    task.repeatDays.forEach { day ->
                        getChipForDay(day)?.isChecked = true
                    }
                }
            }
        }
    }

    private fun setupTemplateSelector() {
        if (templates.isEmpty()) {
            templateSelector.setText("", false)
            return
        }

        val templateNames = listOf(context.getString(R.string.task_select_template)) +
                templates.map { it.name }

        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, templateNames)
        templateSelector.setAdapter(adapter)

        templateSelector.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                return@setOnItemClickListener
            }
            val template = templates[position - 1]
            taskDescInput.setText(template.description)
        }
    }

    private fun setupRepeatTypeListener() {
        repeatTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            weekdaySelector.visibility = when (checkedId) {
                R.id.repeatWeekly -> LinearLayout.VISIBLE
                else -> LinearLayout.GONE
            }
        }
    }

    private fun validateAndSave(): Boolean {
        val name = taskNameInput.text?.toString()?.trim() ?: ""
        val description = taskDescInput.text?.toString()?.trim() ?: ""
        val hourStr = hourInput.text?.toString()?.trim() ?: "0"
        val minuteStr = minuteInput.text?.toString()?.trim() ?: "0"

        var isValid = true

        if (name.isEmpty()) {
            taskNameLayout.error = context.getString(R.string.scheduled_task_validation_name_empty)
            isValid = false
        } else {
            taskNameLayout.error = null
        }

        if (description.isEmpty()) {
            taskDescLayout.error = context.getString(R.string.scheduled_task_validation_desc_empty)
            isValid = false
        } else {
            taskDescLayout.error = null
        }

        val hour = hourStr.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val minute = minuteStr.toIntOrNull()?.coerceIn(0, 59) ?: 0

        val repeatType = when (repeatTypeGroup.checkedRadioButtonId) {
            R.id.repeatDaily -> RepeatType.DAILY
            R.id.repeatWeekly -> RepeatType.WEEKLY
            else -> RepeatType.ONCE
        }

        val repeatDays = if (repeatType == RepeatType.WEEKLY) {
            val days = mutableListOf<Int>()
            if (chipMon.isChecked) days.add(1)
            if (chipTue.isChecked) days.add(2)
            if (chipWed.isChecked) days.add(3)
            if (chipThu.isChecked) days.add(4)
            if (chipFri.isChecked) days.add(5)
            if (chipSat.isChecked) days.add(6)
            if (chipSun.isChecked) days.add(7)

            if (days.isEmpty()) {
                Toast.makeText(context, R.string.scheduled_task_validation_weekdays_empty, Toast.LENGTH_SHORT).show()
                isValid = false
            }
            days
        } else {
            emptyList()
        }

        if (!isValid) {
            return false
        }

        val newTask = ScheduledTask(
            id = task?.id ?: "scheduled_${System.currentTimeMillis()}",
            name = name,
            taskDescription = description,
            hour = hour,
            minute = minute,
            repeatType = repeatType,
            repeatDays = repeatDays,
            isEnabled = enabledSwitch.isChecked,
            createdAt = task?.createdAt ?: System.currentTimeMillis(),
            lastExecutedAt = task?.lastExecutedAt,
            nextExecuteAt = null
        )

        onSave(newTask)
        return true
    }

    private fun getChipForDay(day: Int): Chip? {
        return when (day) {
            1 -> chipMon
            2 -> chipTue
            3 -> chipWed
            4 -> chipThu
            5 -> chipFri
            6 -> chipSat
            7 -> chipSun
            else -> null
        }
    }
}
