// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin.database

import android.content.Context
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ContextWordMatcher {
    private const val TAG = "ContextWordMatcher"

    @Volatile
    private var cachedWords: List<ContextWord>? = null

    data class MatchResult(
        val contextWord: ContextWord,
        val matchedClue: String
    )

    @JvmStatic
    fun findMatch(sentence: String, allWords: List<ContextWord>): ContextWord? {
        return findMatchDetails(sentence, allWords)?.contextWord
    }

    @JvmStatic
    fun findMatchDetails(sentence: String, allWords: List<ContextWord>): MatchResult? {
        if (sentence.isBlank() || allWords.isEmpty()) return null
        val lowerSentence = sentence.lowercase()

        var bestMatch: MatchResult? = null
        var maxLength = -1

        for (word in allWords) {
            val clues = word.clueWords.split('|')
            for (rawClue in clues) {
                val clue = rawClue.trim()
                if (clue.isEmpty()) continue
                val lowerClue = clue.lowercase()
                val regex = Regex("\\b${Regex.escape(lowerClue)}\\b")
                if (regex.containsMatchIn(lowerSentence)) {
                    if (clue.length > maxLength) {
                        maxLength = clue.length
                        bestMatch = MatchResult(word, clue)
                    }
                }
            }
        }
        return bestMatch
    }

    @JvmStatic
    fun loadWordsIfNeeded(context: Context) {
        if (cachedWords != null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = ContextWordsDatabase.getInstance(context)
                val words = db.contextWordDao().getAll()
                cachedWords = words
                Log.i(TAG, "Loaded ${words.size} words into matcher cache")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading words from ContextWordsDatabase", e)
            }
        }
    }

    @JvmStatic
    fun checkAndLog(sentence: String, context: Context) {
        var words = cachedWords
        if (words == null) {
            loadWordsIfNeeded(context)
            Log.i(TAG, "Checking sentence: '$sentence' | words loading...")
            return
        }

        val match = findMatchDetails(sentence, words)
        if (match != null) {
            Log.i(
                TAG,
                "Checking sentence: '$sentence' | MATCH FOUND: target_word='${match.contextWord.targetWord}', clue_phrase='${match.matchedClue}'"
            )
        } else {
            Log.i(TAG, "Checking sentence: '$sentence' | no match")
        }
    }
}
