package com.ferst.mytodoapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ferst.mytodoapp.model.Task


@Dao
interface TaskDao {
    @Insert
    fun insertTask(task: Task): Long // Dodaj zadanie

    @Query("SELECT * FROM tasks WHERE listId = :listId AND status = :status")
    fun getTasksByListIdAndStatus(listId: Long, status: String): List<Task>

    @Query("SELECT * FROM tasks")
    fun getAllTasks(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskById(taskId: Int): Task

    @Query("SELECT * FROM tasks WHERE listId = :listId")
    fun getTasksByListId(listId: Long?): List<Task> // Pobierz zadania z danej listy

    @Query("SELECT * FROM tasks WHERE status = :status")
    fun getTasksByStatus(status: String): List<Task> // Pobierz zadania według statusu

    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY CASE WHEN status = 'niezrobione' THEN 0 ELSE 1 END, id ASC")
    fun getTasksByListIdSorted(listId: Long?): List<Task>

    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt WHERE id = :taskId")
    fun setTaskStatus(taskId: Int, status: String, completedAt: Long?)

    @Update
    fun updateTask(task: Task) // Uniwersalna metoda do aktualizacji całego zadania

    @Query("UPDATE tasks SET status = 'zrobione', completedAt = :completedAt WHERE id = :taskId")
    fun markTaskAsCompleted(completedAt: Long, taskId: Int) // Oznacz jako zrobione

    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt WHERE id = :taskId")
    fun updateTaskStatus(status: String, completedAt: Long?, taskId: Int) // Aktualizuj status zadania

    @Query("DELETE FROM tasks WHERE id = :taskId")
    fun deleteTask(taskId: Int) // Usuń zadanie

    @Query("DELETE FROM tasks WHERE listId = :listId")
    fun deleteTasksByListId(listId: Long)

    @Query("DELETE FROM tasks WHERE status = 'zrobione' AND repeatDays IS NULL AND :currentTime - completedAt >= :timeLimit")
    fun deleteOldCompletedTasks(currentTime: Long, timeLimit: Long)

}
