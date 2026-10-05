// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ContextWordDao {
    @Insert
    fun insertAll(entries: List<ContextWord>)

    @Query("SELECT * FROM context_words")
    fun getAll(): List<ContextWord>

    @Query("SELECT COUNT(*) FROM context_words")
    fun count(): Int
}
