package com.ferst.mytodoapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,  // Autoinkrementacja
    var title: String,                                 // Tytuł zadania
    var listId: Long?,                                 // ID listy zadań
    var status: String= "niezrobione",                 // Status: niezrobione/zrobione/usunięte
    val createdAt: Long,                               // Data i czas utworzenia (timestamp)
    var completedAt: Long? = null,                     // Data i czas zakończenia (może być null)/**
    var repeatDays: String? = null                     // Dni powtarzania, np. "Pn,Wt,Śr"
)
