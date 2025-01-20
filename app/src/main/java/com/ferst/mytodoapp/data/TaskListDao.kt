package com.ferst.mytodoapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Transaction
import androidx.room.Update
import com.ferst.mytodoapp.model.TaskList

@Dao
interface TaskListDao {
    @Insert
    fun insertTaskList(taskList: TaskList):Long? // Dodaj listę zadań

    @Query("SELECT * FROM lists ORDER BY orderIndex")
    fun getAllLists(): List<TaskList> // Pobierz wszystkie listy według kolejności

    @Query("SELECT * FROM lists WHERE name != 'Wszystkie'")
    fun getUserLists(): List<TaskList> // Pobierz tylko listy użytkownika

    @Query("UPDATE lists SET orderIndex = :newOrderIndex WHERE id = :listId")
    fun updateOrderIndex(listId: Long, newOrderIndex: Int)

    @Delete
    fun deleteTaskList(taskList: TaskList) // Usuń konkretną listę

    @Query("DELETE FROM lists WHERE id = :listId")
    fun deleteTaskList(listId: Long)

    @Update
    fun updateTaskList(taskList: TaskList)

    @Query("DELETE FROM tasks WHERE listId = :listId")
    fun deleteTasksByListId(listId: Long)

    @Transaction
    fun deleteListAndItsTasks(listId: Long) {
        deleteTasksByListId(listId)
        deleteTaskList(listId)
    }
}
