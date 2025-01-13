package com.ferst.mytodoapp

import android.app.AlertDialog
import com.ferst.mytodoapp.data.AppDatabase
import com.ferst.mytodoapp.model.Task
import com.ferst.mytodoapp.model.TaskList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.app.Dialog
import android.content.ClipData
import android.graphics.drawable.GradientDrawable
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.View
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ferst.mytodoapp.data.TaskListDao
import java.util.Collections

class MainActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var adapter: TaskAdapter
    private var selectedListId: Long? = null // null oznacza zakładkę "Wszystkie"
    private val ALL_TASKS_ID = 1L // Stała identyfikująca "Wszystkie"
    private val taskListsCache = Collections.synchronizedList(mutableListOf<TaskList>()) // Przechowywanie list w pamięci

    val MIGRATION_2_2 = object : Migration(2, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE lists ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicjalizacja bazy danych
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "tasks.db"
        ).addMigrations(MIGRATION_2_2) // Dodanie migracji
            .build()

        // Sprawdzenie powtarzających się zadań
        checkAndResetRepeatingTasks()

        // Domyślna zakładka to "Wszystkie"
        selectedListId = ALL_TASKS_ID

        // Inicjalizacja RecyclerView i adaptera
        val recyclerView = findViewById<RecyclerView>(R.id.task_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Inicjalizacja adaptera jako pola klasy
        adapter = TaskAdapter(
            tasks = emptyList(),
            onTaskStatusChanged = { updatedTask, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.taskDao().updateTask(updatedTask) // Zapisujemy zmienione zadanie
                    }
                    refreshTaskList() // Odśwież listę po zmianie
                }
            },
            onTaskEdit = { task -> showEditTaskDialog(task) },
            onTaskDelete = { task ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.taskDao().deleteTask(task.id)
                    }
                    refreshTaskList()
                }
            }
        )

        recyclerView.adapter = adapter

        // Ładowanie list zadań w tle
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (db.taskListDao().getAllLists().isEmpty()) {
                    db.taskListDao().insertTaskList(TaskList(name = "Wszystkie"))
                }
            }
            loadTaskLists()
            refreshTaskList()
        }

        // Obsługa przycisków
        findViewById<FloatingActionButton>(R.id.add_task_button).setOnClickListener {
            showAddTaskDialog()
        }
        findViewById<ImageView>(R.id.add_list_button).setOnClickListener {
            showAddListDialog()
        }
    }

    private fun refreshTaskList() {
        lifecycleScope.launch {
            adapter.updateTasks(emptyList()) // Wyczyszczenie adaptera przed załadowaniem nowych danych

            val (incompleteTasks, completeTasks) = withContext(Dispatchers.IO) {
                if (selectedListId == ALL_TASKS_ID) {
                    val incomplete = db.taskDao().getTasksByStatus("niezrobione")
                    val complete = db.taskDao().getTasksByStatus("zrobione")
                    Pair(incomplete, complete)
                } else {
                    val incomplete = db.taskDao().getTasksByListIdAndStatus(selectedListId!!, "niezrobione")
                    val complete = db.taskDao().getTasksByListIdAndStatus(selectedListId!!, "zrobione")
                    Pair(incomplete, complete)
                }
            }

            val allTasks = (incompleteTasks + completeTasks).map { it.copy() }

            adapter.updateTasks(allTasks)
            val recyclerView = findViewById<RecyclerView>(R.id.task_list)
            val emptyView = findViewById<TextView>(R.id.empty_view)
            if (allTasks.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
            }

            // Przygotowanie danych kategorii
            val updatedLists = withContext(Dispatchers.IO) {
                taskListsCache.map { list ->
                    val taskCount = if (list.id == ALL_TASKS_ID) {
                        db.taskDao().getTasksByStatus("niezrobione").size
                    } else {
                        db.taskDao().getTasksByListIdAndStatus(list.id, "niezrobione").size
                    }
                    list.copy(taskCount = taskCount)
                }
            }

            synchronized(taskListsCache) {
                taskListsCache.clear()
                taskListsCache.addAll(updatedLists)
            }

            populateCategoryMenu(taskListsCache)
        }
    }

    private fun showAddTaskDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val taskNameEditText = dialogView.findViewById<EditText>(R.id.task_name)
        taskNameEditText.setHorizontallyScrolling(false)
        taskNameEditText.isVerticalScrollBarEnabled = true
        taskNameEditText.maxLines = 4
        taskNameEditText.movementMethod = ScrollingMovementMethod()
        val taskCategorySpinner = dialogView.findViewById<Spinner>(R.id.task_category)
        val repeatButton = dialogView.findViewById<Button>(R.id.repeat_task_button)
        val selectedDaysText = dialogView.findViewById<TextView>(R.id.repeat_days_selected)
        val addTaskButton = dialogView.findViewById<Button>(R.id.add_task_button)
        val cancelTaskButton = dialogView.findViewById<Button>(R.id.cancel_task_button)
        var selectedDays = mutableListOf<String>()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        lifecycleScope.launch {
            val taskLists = withContext(Dispatchers.IO) {
                db.taskListDao().getAllLists()
            }

            if (taskLists.isEmpty()) {
                taskCategorySpinner.visibility = View.GONE // Ukryj spinner
            } else {
                taskCategorySpinner.visibility = View.VISIBLE // Pokaż spinner
                val listNames = taskLists.map { it.name }
                val adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    listNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                taskCategorySpinner.adapter = adapter
            }
        }

        repeatButton.setOnClickListener {
            showRepeatDaysDialog { days ->
                selectedDays = days.toMutableList()
                val daysText = if (selectedDays.isNotEmpty()) {
                    selectedDays.joinToString(", ")
                } else {
                    "Brak"
                }
                selectedDaysText.text = "Wybrane dni: $daysText"
            }
        }

        addTaskButton.setOnClickListener {
            val taskName = taskNameEditText.text.toString()
            val selectedListName = taskCategorySpinner.selectedItem as? String

            if (taskName.isNotBlank() && selectedListName != null) {
                lifecycleScope.launch {
                    val selectedTaskList = withContext(Dispatchers.IO) {
                        db.taskListDao().getAllLists().find { it.name == selectedListName }
                    }

                    val listId = selectedTaskList?.id // null oznacza "Wszystkie"

                    withContext(Dispatchers.IO) {
                        db.taskDao().insertTask(
                            Task(
                                title = taskName,
                                listId = listId,
                                status = "niezrobione",
                                createdAt = System.currentTimeMillis(),
                                completedAt = null,
                                repeatDays = if (selectedDays.isNotEmpty()) selectedDays.joinToString(",") else null
                            )
                        )
                    }
                    refreshTaskList() // Odświeżanie listy zadań po dodaniu nowego zadania
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(this, "Podaj nazwę zadania i wybierz listę", Toast.LENGTH_SHORT).show()
            }
        }

        cancelTaskButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        dialog.show()
    }

    private fun showRepeatDaysDialog(
        preselectedDays: List<String> = emptyList(),
        onDaysSelected: (List<String>) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_repeat_task, null)
        val saveButton = dialogView.findViewById<Button>(R.id.save_button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)

        val daysCheckboxes = listOf(
            dialogView.findViewById<CheckBox>(R.id.check_pn) to "Pn",
            dialogView.findViewById<CheckBox>(R.id.check_wt) to "Wt",
            dialogView.findViewById<CheckBox>(R.id.check_sr) to "Śr",
            dialogView.findViewById<CheckBox>(R.id.check_czw) to "Czw",
            dialogView.findViewById<CheckBox>(R.id.check_pt) to "Pt",
            dialogView.findViewById<CheckBox>(R.id.check_sb) to "Sb",
            dialogView.findViewById<CheckBox>(R.id.check_nd) to "Nd"
        )

        // Ustawienie checkboxów na podstawie wstępnie wybranych dni
        daysCheckboxes.forEach { (checkBox, day) ->
            checkBox.isChecked = preselectedDays.contains(day)
        }

        val dialog = Dialog(this)
        dialog.setContentView(dialogView)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        saveButton.setOnClickListener {
            val selectedDays = daysCheckboxes
                .filter { it.first.isChecked }
                .map { it.second }
            onDaysSelected(selectedDays)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadTaskLists() {
        lifecycleScope.launch {
            try {
                // Pobieranie list w tle
                val lists = withContext(Dispatchers.IO) {
                    db.taskListDao().getAllLists()
                }

                // Aktualizowanie cache i widoków na głównym wątku
                taskListsCache.clear()
                taskListsCache.addAll(lists)
                populateCategoryMenu(taskListsCache)

            } catch (e: Exception) {
                // Obsługa błędów (opcjonalne logowanie)
                e.printStackTrace()
            }
        }
    }

    private fun populateCategoryMenu(taskLists: List<TaskList>) {
        val container = findViewById<LinearLayout>(R.id.category_container)
        val currentChildCount = container.childCount
        val addButtonTag = "addButton"

        taskLists.forEachIndexed { index, list ->
            // Jeśli aktualnie wybrana lista, używamy pełnej nazwy, inaczej skracamy
            val displayName = if (list.id == selectedListId) list.name else truncateText(list.name)
            val taskCountText = "$displayName (${list.taskCount})"

            val listContainer = if (index < currentChildCount - 1) {
                container.getChildAt(index) as LinearLayout
            } else {
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ).apply {
                        setMargins(16, 0, 16, 0)
                    }
                }.also { container.addView(it, index) }
            }

            val textView = listContainer.findViewWithTag<TextView>(list.id.toString()) ?: TextView(this).apply {
                tag = list.id.toString()
                textSize = 16f
                setTextColor(resources.getColor(android.R.color.white, null))
                setPadding(16, 0, 16, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0,
                    1f
                )
                setOnClickListener {
                    onCategorySelected(list.id)
                }

                if (list.id != ALL_TASKS_ID) {
                    setOnLongClickListener {
                        showListOptionsDialog(list)
                        true
                    }
                }
                listContainer.addView(this)
            }

            val underline = listContainer.findViewWithTag<View>("underline_${list.id}") ?: View(this).apply {
                tag = "underline_${list.id}"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    10
                )
                val backgroundDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(resources.getColor(android.R.color.white, null))
                    cornerRadius = 5f
                }
                background = backgroundDrawable
                listContainer.addView(this)
            }

            // Aktualizacja treści widoku
            textView.text = taskCountText
            underline.visibility = if (list.id == selectedListId) View.VISIBLE else View.INVISIBLE
        }

        if (currentChildCount > taskLists.size + 1) {
            container.removeViews(taskLists.size, currentChildCount - taskLists.size - 1)
        }

        val addButton = container.findViewWithTag<ImageView>(addButtonTag) ?: ImageView(this).apply {
            tag = addButtonTag
            setImageResource(R.drawable.plus)
            layoutParams = LinearLayout.LayoutParams(
                70,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(16, 0, 16, 0)
                gravity = Gravity.CENTER_VERTICAL
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { showAddListDialog() }
            setColorFilter(resources.getColor(android.R.color.white, null))
            container.addView(this)
        }
    }

    private fun truncateText(text: String): String {
        return if (text.length > 13) "${text.take(10)}..." else text
    }

    private fun onCategorySelected(listId: Long?) {
        selectedListId = listId ?: ALL_TASKS_ID
        val container = findViewById<LinearLayout>(R.id.category_container)
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is LinearLayout) {
                val textView = child.getChildAt(0) as? TextView
                val underline = child.getChildAt(1) as? View
                val listIdTag = textView?.tag as? Long

                if (listIdTag == selectedListId) {
                    // Zaktualizuj nazwę tylko, jeśli lista została znaleziona w cache
                    taskListsCache.find { it.id == listIdTag }?.let { list ->
                        textView?.text = list.name
                    }
                    underline?.visibility = View.VISIBLE
                } else {
                    // Nie zmieniaj tekstu, jeśli lista nie jest znaleziona
                    taskListsCache.find { it.id == listIdTag }?.let { list ->
                        textView?.text = truncateText(list.name)
                    }
                    underline?.visibility = View.INVISIBLE
                }
            }
        }
        refreshTaskList()
    }

    private fun showAddListDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_list, null)
        val listNameInput = dialogView.findViewById<EditText>(R.id.list_name_input)
        listNameInput.setHorizontallyScrolling(false)
        listNameInput.isVerticalScrollBarEnabled = true
        listNameInput.maxLines = 4
        listNameInput.movementMethod = ScrollingMovementMethod()
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val saveButton = dialogView.findViewById<Button>(R.id.save_button)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        saveButton.setOnClickListener {
            val listName = listNameInput.text.toString()
            if (listName.isNotBlank()) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val maxOrderIndex = db.taskListDao().getAllLists().maxOfOrNull { it.orderIndex } ?: 0
                        db.taskListDao().insertTaskList(TaskList(name = listName, orderIndex = maxOrderIndex + 1))
                    }
                    loadTaskLists()
                    dialog.dismiss()
                    refreshTaskList()
                }
            } else {
                Toast.makeText(this, "Podaj nazwę listy", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun checkAndResetRepeatingTasks() {
        val currentDay = getCurrentDayOfWeek() // Pobierz skróconą nazwę dnia tygodnia, np. "Śr"
        val currentDate = System.currentTimeMillis() // Aktualny timestamp
        val timeLimit = 24 * 60 * 60 * 1000L // 24 godziny w milisekundach

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val tasks = db.taskDao().getAllTasks() // Pobierz wszystkie zadania z bazy danych

                tasks.forEach { task ->
                    // Obsługa powtarzających się zadań
                    val repeatDays = task.repeatDays
                    if (repeatDays != null && repeatDays.split(",").contains(currentDay)) {
                        // Jeśli zadanie ma dni powtarzania i zawiera dzisiejszy dzień
                        if (!isTaskCompletedToday(task, currentDate)) {
                            val updatedTask = task.copy(
                                status = "niezrobione",
                                completedAt = null
                            )
                            db.taskDao().updateTask(updatedTask)
                        }
                    }

                    // Usuwanie zadań zrobionych i bez powtarzania
                    if (task.status == "zrobione" && repeatDays == null) {
                        val taskAge = currentDate - (task.completedAt ?: 0)
                        if (taskAge >= timeLimit) {
                            db.taskDao().deleteTask(task.id)
                        }
                    }
                }
            }
            refreshTaskList() // Odśwież listę zadań po sprawdzeniu i usunięciu
        }
    }

    /**
     * Funkcja sprawdza, czy zadanie zostało zakończone dzisiaj.
     */
    private fun isTaskCompletedToday(task: Task, currentDate: Long): Boolean {
        task.completedAt?.let { completedAt ->
            val taskCompletedDay = getDayStartTimestamp(completedAt)
            val todayStart = getDayStartTimestamp(currentDate)
            return taskCompletedDay == todayStart
        }
        return false
    }

    /**
     * Funkcja pobiera początek dnia na podstawie timestampu.
     */
    private fun getDayStartTimestamp(timestamp: Long): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /**
     * Funkcja zwraca skróconą nazwę bieżącego dnia tygodnia (np. "Pn", "Wt").
     */
    private fun getCurrentDayOfWeek(): String {
        val daysOfWeek = listOf("Nd", "Pn", "Wt", "Śr", "Czw", "Pt", "Sb")
        val calendar = java.util.Calendar.getInstance()
        val dayIndex = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1 // Niedziela = 0
        return daysOfWeek[dayIndex]
    }

    private fun showEditTaskDialog(task: Task) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val taskNameEditText = dialogView.findViewById<EditText>(R.id.task_name)
        taskNameEditText.setHorizontallyScrolling(false)
        taskNameEditText.isVerticalScrollBarEnabled = true
        taskNameEditText.maxLines = 4
        taskNameEditText.movementMethod = ScrollingMovementMethod()
        val saveButton = dialogView.findViewById<Button>(R.id.add_task_button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_task_button)
        val taskCategorySpinner = dialogView.findViewById<Spinner>(R.id.task_category)
        val repeatDaysTextView = dialogView.findViewById<TextView>(R.id.repeat_days_selected)
        val repeatButton = dialogView.findViewById<Button>(R.id.repeat_task_button)

        // Wypełnij pole nazwy zadania
        taskNameEditText.setText(task.title)

        // Wyświetl wybrane dni powtarzania
        repeatDaysTextView.text = "Wybrane dni: " + task.repeatDays?.split(",")?.joinToString(", ") ?: "Wybrane dni: -"

        // Obsługa przycisku "Powtarzaj zadanie"
        repeatButton.setOnClickListener {
            val currentDays = task.repeatDays?.split(",") ?: emptyList()
            showRepeatDaysDialog(preselectedDays = currentDays) { selectedDays ->
                task.repeatDays = selectedDays.joinToString(",") // Zapisz wybrane dni
                repeatDaysTextView.text = "Wybrane dni: ${selectedDays.joinToString(", ")}" // Aktualizuj widok
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        lifecycleScope.launch {
            val taskLists = withContext(Dispatchers.IO) {
                db.taskListDao().getAllLists()
            }

            if (taskLists.isNotEmpty()) {
                val listNames = taskLists.map { it.name }
                val adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    listNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                taskCategorySpinner.adapter = adapter

                // Ustaw domyślną listę przypisaną do zadania
                val currentListIndex = taskLists.indexOfFirst { it.id == task.listId }
                if (currentListIndex != -1) {
                    taskCategorySpinner.setSelection(currentListIndex)
                }
            } else {
                taskCategorySpinner.visibility = View.GONE
            }

            cancelButton.setOnClickListener {
                dialog.dismiss()
            }
        }

        saveButton.setOnClickListener {
            val updatedTitle = taskNameEditText.text.toString()
            val selectedListName = taskCategorySpinner.selectedItem as? String

            if (updatedTitle.isNotBlank() && selectedListName != null) {
                lifecycleScope.launch {
                    val selectedTaskList = withContext(Dispatchers.IO) {
                        db.taskListDao().getAllLists().find { it.name == selectedListName }
                    }

                    if (selectedTaskList != null) {
                        task.title = updatedTitle
                        task.listId = selectedTaskList.id
                    }

                    withContext(Dispatchers.IO) {
                        db.taskDao().updateTask(task)
                    }
                    refreshTaskList()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(this, "Podaj nazwę zadania i wybierz listę", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun showEditListDialog(list: TaskList) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_list, null)
        val listNameInput = dialogView.findViewById<EditText>(R.id.list_name_input)
        listNameInput.setText(list.name)
        listNameInput.setHorizontallyScrolling(false)
        listNameInput.isVerticalScrollBarEnabled = true
        listNameInput.maxLines = 4
        listNameInput.movementMethod = ScrollingMovementMethod()

        val saveButton = dialogView.findViewById<Button>(R.id.save_button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Edytuj nazwę listy")
            .create()

        saveButton.setOnClickListener {
            val newName = listNameInput.text.toString()
            if (newName.isNotBlank()) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        list.name = newName
                        db.taskListDao().updateTaskList(list)
                    }
                    loadTaskLists()
                }
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Nazwa listy nie może być pusta", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun confirmAndDeleteList(list: TaskList) {
        AlertDialog.Builder(this)
            .setTitle("Usuwanie listy")
            .setMessage("Czy na pewno chcesz usunąć listę '${list.name}' wraz z jej zadaniami?")
            .setPositiveButton("Usuń") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        // Usuń zadania powiązane z listą
                        db.taskDao().deleteTasksByListId(list.id)
                        // Usuń samą listę
                        db.taskListDao().deleteTaskList(list)
                    }
                    loadTaskLists()
                    refreshTaskList()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun showListOptionsDialog(list: TaskList) {
        val options = arrayOf("Edytuj", "Usuń")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Opcje listy")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> showEditListDialog(list)
                1 -> confirmAndDeleteList(list)
            }
        }
        builder.show()
    }
}