// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "context_words")
data class ContextWord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val targetWord: String,
    val clueWords: String,
    val whyExplanation: String,
    val teluguTranslation: String
)
