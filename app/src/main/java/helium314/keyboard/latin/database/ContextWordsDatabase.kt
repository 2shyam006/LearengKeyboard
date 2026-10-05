// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ContextWord::class], version = 1, exportSchema = false)
abstract class ContextWordsDatabase : RoomDatabase() {
    abstract fun contextWordDao(): ContextWordDao

    companion object {
        @Volatile
        private var INSTANCE: ContextWordsDatabase? = null

        fun getInstance(context: Context): ContextWordsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContextWordsDatabase::class.java,
                    "context_words.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
