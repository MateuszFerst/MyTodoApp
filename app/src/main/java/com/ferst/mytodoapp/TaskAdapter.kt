package com.ferst.mytodoapp

import android.app.AlertDialog
import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.ferst.mytodoapp.databinding.ItemTaskBinding
import com.ferst.mytodoapp.model.Task

class TaskAdapter(
    private var tasks: List<Task>,
    private val onTaskStatusChanged: (Task, Boolean) -> Unit, // Callback do zmiany statusu
    private val onTaskEdit: (Task) -> Unit, // Callback do edycji zadania
    private val onTaskDelete: (Task) -> Unit // Callback do usunięcia zadania
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(task: Task) {
            // Ustaw dane w widoku
            binding.taskTitle.text = task.title

            // Usuń poprzedni listener, aby uniknąć konfliktów
            binding.taskCheckbox.setOnCheckedChangeListener(null)

            // Ustaw stan checkboxa
            binding.taskCheckbox.isChecked = task.status == "zrobione"

            // Wygląd wizualny zrobionych zadań
            binding.taskTitle.apply {
                alpha = if (task.status == "zrobione") 0.5f else 1.0f
                paintFlags = if (task.status == "zrobione") {
                    paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            }

            // Wyświetlanie ikony powtarzania
            if (!task.repeatDays.isNullOrEmpty()) {
                binding.iconAutorenew.visibility = View.VISIBLE
            } else {
                binding.iconAutorenew.visibility = View.GONE
            }

            // Obsługa zmiany statusu
            binding.taskCheckbox.setOnCheckedChangeListener { _, isChecked ->
                val updatedTask = task.copy(
                    status = if (isChecked) "zrobione" else "niezrobione",
                    completedAt = if (isChecked) System.currentTimeMillis() else null
                )
                onTaskStatusChanged(updatedTask, isChecked) // Przekazujemy kopię zmienionego zadania
            }

            // Obsługa edycji i usuwania zadania
            binding.root.setOnLongClickListener {
                showTaskOptions(binding.root.context, task)
                true
            }
        }

        private fun showTaskOptions(context: Context, task: Task) {
            val options = arrayOf("Edytuj", "Usuń")
            AlertDialog.Builder(context)
                .setTitle("Opcje zadania")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> onTaskEdit(task) // Wywołaj edycję zadania
                        1 -> confirmDeleteTask(context, task) // Pokaż dialog usunięcia
                    }
                }
                .show()
        }

        private fun confirmDeleteTask(context: Context, task: Task) {
            AlertDialog.Builder(context)
                .setTitle("Usuń zadanie")
                .setMessage("Czy na pewno chcesz usunąć to zadanie?")
                .setPositiveButton("Usuń") { _, _ -> onTaskDelete(task) }
                .setNegativeButton("Anuluj", null)
                .show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        val diffCallback = TaskDiffCallback(tasks, newTasks)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        tasks = newTasks
        diffResult.dispatchUpdatesTo(this)
    }

    class TaskDiffCallback(
        private val oldList: List<Task>,
        private val newList: List<Task>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
