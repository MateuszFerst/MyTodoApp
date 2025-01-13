package com.ferst.mytodoapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ferst.mytodoapp.model.Task
import com.ferst.mytodoapp.model.TaskList

@Database(entities = [Task::class, TaskList::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migracja z wersji 1 do wersji 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Dodaj kolumnę repeatDays do tabeli tasks
                database.execSQL("ALTER TABLE tasks ADD COLUMN repeatDays TEXT")
            }
        }

        // Migracja z wersji 2 do wersji 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Dodaj kolumnę taskCount do tabeli lists
                database.execSQL("ALTER TABLE lists ADD COLUMN taskCount INTEGER DEFAULT 0 NOT NULL")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "my_todo_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
