package com.ferst.mytodoapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lists")
data class TaskList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,  // Autoinkrementacja
    var name: String,  // Nazwa listy
    var orderIndex: Int = 0, // Kolejność listy
    var taskCount: Int = 0
)
