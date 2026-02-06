package com.kevinluo.autoglm.scheduled

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.settings.TaskTemplate

/**
 * 定时任务列表页面
 */
class ScheduledTasksActivity : AppCompatActivity() {

    private lateinit var manager: ScheduledTaskManager
    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var fabAddTask: View
    private lateinit var backBtn: ImageButton
    private var adapter: ScheduledTasksAdapter? = null
    private var tasks: MutableList<ScheduledTask> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduled_tasks)

        manager = ScheduledTaskManager.getInstance(this)

        initViews()
        setupRecyclerView()
        setupListeners()
        loadTasks()
    }

    private fun initViews() {
        backBtn = findViewById(R.id.backBtn)
        tasksRecyclerView = findViewById(R.id.tasksRecyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        fabAddTask = findViewById(R.id.fabAddTask)
    }

    private fun setupRecyclerView() {
        adapter = ScheduledTasksAdapter(
            tasks = tasks,
            onTaskEnabledChange = { task, enabled ->
                manager.updateTaskEnabled(task.id, enabled)
                loadTasks()
            },
            onEditClick = { task ->
                showEditTaskDialog(task)
            },
            onDeleteClick = { task ->
                showDeleteConfirmDialog(task)
            }
        )
        tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        tasksRecyclerView.adapter = adapter
    }

    private fun setupListeners() {
        backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        fabAddTask.setOnClickListener {
            showAddTaskDialog()
        }
    }

    private fun loadTasks() {
        tasks.clear()
        tasks.addAll(manager.getAllTasks().sortedBy { it.nextExecuteAt ?: Long.MAX_VALUE })
        adapter?.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (tasks.isEmpty()) {
            tasksRecyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            tasksRecyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        }
    }

    private fun showAddTaskDialog() {
        val dialog = ScheduledTaskEditDialog(
            context = this,
            settingsManager = com.kevinluo.autoglm.settings.SettingsManager(this),
            task = null,
            onSave = { task ->
                manager.saveTask(task)
                loadTasks()
                Toast.makeText(this, R.string.scheduled_task_saved, Toast.LENGTH_SHORT).show()
            }
        )
        dialog.show()
    }

    private fun showEditTaskDialog(task: ScheduledTask) {
        val dialog = ScheduledTaskEditDialog(
            context = this,
            settingsManager = com.kevinluo.autoglm.settings.SettingsManager(this),
            task = task,
            onSave = { updatedTask ->
                manager.saveTask(updatedTask)
                loadTasks()
                Toast.makeText(this, R.string.scheduled_task_saved, Toast.LENGTH_SHORT).show()
            }
        )
        dialog.show()
    }

    private fun showDeleteConfirmDialog(task: ScheduledTask) {
        AlertDialog.Builder(this)
            .setTitle(R.string.scheduled_task_delete)
            .setMessage(R.string.scheduled_task_delete_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                manager.deleteTask(task.id)
                loadTasks()
                Toast.makeText(this, R.string.scheduled_task_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    /**
     * Adapter for scheduled tasks RecyclerView
     */
    private inner class ScheduledTasksAdapter(
        private val tasks: List<ScheduledTask>,
        private val onTaskEnabledChange: (ScheduledTask, Boolean) -> Unit,
        private val onEditClick: (ScheduledTask) -> Unit,
        private val onDeleteClick: (ScheduledTask) -> Unit
    ) : RecyclerView.Adapter<ScheduledTasksAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val enableSwitch: SwitchMaterial = view.findViewById(R.id.enableSwitch)
            val taskName: TextView = view.findViewById(R.id.taskName)
            val taskTime: TextView = view.findViewById(R.id.taskTime)
            val taskNextExecute: TextView = view.findViewById(R.id.taskNextExecute)
            val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scheduled_task, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val task = tasks[position]

            holder.taskName.text = task.name
            holder.taskTime.text = "${task.getRepeatTypeDescription()} ${task.getTimeDescription()}"
            holder.taskNextExecute.text = getString(
                R.string.scheduled_task_next_format,
                manager.getNextExecuteDescription(task)
            )

            holder.enableSwitch.isChecked = task.isEnabled
            holder.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                onTaskEnabledChange(task, isChecked)
            }

            holder.btnEdit.setOnClickListener {
                onEditClick(task)
            }

            holder.btnDelete.setOnClickListener {
                onDeleteClick(task)
            }

            holder.itemView.alpha = if (task.isEnabled) 1.0f else 0.6f
        }

        override fun getItemCount() = tasks.size
    }
}
