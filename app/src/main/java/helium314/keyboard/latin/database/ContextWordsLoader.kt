// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin.database

import android.content.Context
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object ContextWordsLoader {
    private const val TAG = "ContextWordsLoader"

    fun load(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val db = ContextWordsDatabase.getInstance(appContext)
            val dao = db.contextWordDao()

            if (dao.count() > 0) {
                Log.i(TAG, "Database already loaded, skipping import.")
                return@launch
            }

            val entries = mutableListOf<ContextWord>()
            try {
                appContext.assets.open("context_words.csv").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                        val header = reader.readLine()
                        if (header != null) {
                            var line = reader.readLine()
                            while (line != null) {
                                if (line.isNotBlank()) {
                                    val fields = parseCsvLine(line)
                                    if (fields.size >= 4) {
                                        entries.add(
                                            ContextWord(
                                                targetWord = fields[0],
                                                clueWords = fields[1],
                                                whyExplanation = fields[2],
                                                teluguTranslation = fields[3]
                                            )
                                        )
                                    }
                                }
                                line = reader.readLine()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading context_words.csv from assets", e)
                return@launch
            }

            if (entries.isNotEmpty()) {
                dao.insertAll(entries)
            }

            // Post-load validation logging
            val loadedEntries = dao.getAll()
            Log.i(TAG, "Total rows loaded: ${loadedEntries.size}")

            // Check clue word duplicate warnings
            val clueToTargetWords = mutableMapOf<String, MutableSet<String>>()
            for (entry in loadedEntries) {
                val clues = entry.clueWords.split('|').map { it.trim() }.filter { it.isNotEmpty() }
                for (clue in clues) {
                    clueToTargetWords.getOrPut(clue.lowercase()) { mutableSetOf() }.add(entry.targetWord)
                }
            }
            for ((clue, targetWords) in clueToTargetWords) {
                if (targetWords.size > 1) {
                    Log.w(TAG, "WARNING: Clue word '$clue' appears under more than one target_word: ${targetWords.joinToString(", ")}")
                }
            }

            // Check why_explanation length warnings
            for (entry in loadedEntries) {
                val words = entry.whyExplanation.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (words.size > 20) {
                    Log.w(TAG, "WARNING: why_explanation for target_word '${entry.targetWord}' exceeds 20 words (${words.size} words): '${entry.whyExplanation}'")
                }
            }

            // Check empty telugu_translation warnings
            for (entry in loadedEntries) {
                if (entry.teluguTranslation.isBlank()) {
                    Log.w(TAG, "WARNING: telugu_translation for target_word '${entry.targetWord}' is empty")
                }
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    current.append(c)
                }
            } else {
                if (c == '"') {
                    inQuotes = true
                } else if (c == ',') {
                    result.add(current.toString().trim())
                    current.clear()
                } else {
                    current.append(c)
                }
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }
}
