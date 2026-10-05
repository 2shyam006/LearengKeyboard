// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ContextWord
import helium314.keyboard.latin.database.ContextWordMatcher
import helium314.keyboard.latin.database.ContextWordsDatabase
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class ContextBarManager(private val latinIME: LatinIME) {

    private var rootView: View? = null
    private var mainRow: View? = null
    private var line1: TextView? = null
    private var line2: TextView? = null
    private var arrow: ImageView? = null
    private var dropdownPanel: View? = null
    private var btnReadAloud: TextView? = null
    private var btnTranslate: TextView? = null

    private var currentMatch: ContextWordMatcher.MatchResult? = null
    private var activeTargetWord: String? = null
    private var isBarShowing: Boolean = false
    private var isDropdownExpanded: Boolean = false
    private var isTeluguView: Boolean = false
    private var readAloudTapCount: Int = 0

    private var allWordsCache: List<ContextWord>? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized: Boolean = false

    fun init(containerView: View) {
        val root = containerView.findViewById<View>(R.id.context_bar_root) ?: containerView
        rootView = root
        mainRow = root.findViewById(R.id.context_bar_main_row)
        line1 = root.findViewById(R.id.context_bar_line1)
        line2 = root.findViewById(R.id.context_bar_line2)
        arrow = root.findViewById(R.id.context_bar_arrow)
        dropdownPanel = root.findViewById(R.id.context_bar_dropdown_panel)
        btnReadAloud = root.findViewById(R.id.context_bar_btn_read_aloud)
        btnTranslate = root.findViewById(R.id.context_bar_btn_translate)

        Log.i(TAG, "ContextBarManager init completed. rootView found: ${rootView != null}")
        setupListeners()
    }

    private fun getOrInitRoot(): View? {
        if (rootView == null) {
            val inputView = latinIME.inputView
            if (inputView != null) {
                init(inputView)
            }
        }
        return rootView
    }

    private fun setupListeners() {
        mainRow?.setOnClickListener {
            val match = currentMatch ?: return@setOnClickListener
            val targetWord = match.contextWord.targetWord
            Log.i(TAG, "Bar-tap-to-insert triggered: target_word='$targetWord'")

            latinIME.onTextInput(targetWord)

            if (isBarShowing) {
                isBarShowing = false
                activeTargetWord = null
                currentMatch = null
                Log.i(TAG, "Bar hidden")
                animateCircularReveal(show = false)
            }
        }

        arrow?.setOnClickListener {
            isDropdownExpanded = !isDropdownExpanded
            Log.i(TAG, "Arrow tapped: expanded=$isDropdownExpanded")

            if (isDropdownExpanded) {
                dropdownPanel?.visibility = View.VISIBLE
                arrow?.animate()?.rotation(180f)?.setDuration(200)?.start()
            } else {
                dropdownPanel?.visibility = View.GONE
                arrow?.animate()?.rotation(0f)?.setDuration(200)?.start()
            }
            latinIME.requestInsetsUpdate()
        }

        btnReadAloud?.setOnClickListener {
            val match = currentMatch ?: return@setOnClickListener
            val targetWord = match.contextWord.targetWord

            readAloudTapCount++
            val speechRate = if (readAloudTapCount == 1) 1.0f else 0.5f
            Log.i(TAG, "Read aloud tapped: target_word='$targetWord', speech_rate=$speechRate")

            speakWord(targetWord, speechRate)
        }

        btnTranslate?.setOnClickListener {
            val match = currentMatch ?: return@setOnClickListener
            isTeluguView = !isTeluguView
            Log.i(TAG, "Translate tapped: showing_telugu=$isTeluguView")

            if (isTeluguView) {
                line1?.text = match.contextWord.teluguTranslation
                line2?.visibility = View.GONE
                btnTranslate?.text = "English"
            } else {
                line1?.text = "Use ${match.contextWord.targetWord} — you used ${match.matchedClue}"
                line2?.text = match.contextWord.whyExplanation
                line2?.visibility = View.VISIBLE
                btnTranslate?.text = "Translate"
            }
            latinIME.requestInsetsUpdate()
        }
    }

    fun onSentenceUpdated(sentence: String) {
        ContextWordMatcher.checkAndLog(sentence, latinIME)

        val words = allWordsCache
        if (words == null) {
            loadWordsAndCheck(sentence)
            return
        }

        val matchResult = ContextWordMatcher.findMatchDetails(sentence, words)
        updateMatch(matchResult)
    }

    private fun loadWordsAndCheck(sentence: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = ContextWordsDatabase.getInstance(latinIME)
                val words = db.contextWordDao().getAll()
                allWordsCache = words
                val matchResult = ContextWordMatcher.findMatchDetails(sentence, words)
                latinIME.mHandler.post {
                    updateMatch(matchResult)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading words for ContextBar", e)
            }
        }
    }

    private fun updateMatch(matchResult: ContextWordMatcher.MatchResult?) {
        val root = getOrInitRoot()
        if (root == null) {
            Log.w(TAG, "updateMatch called but rootView is still null!")
            return
        }

        if (matchResult != null) {
            val targetWord = matchResult.contextWord.targetWord
            if (!isBarShowing || activeTargetWord != targetWord) {
                activeTargetWord = targetWord
                currentMatch = matchResult
                readAloudTapCount = 0
                isTeluguView = false
                isDropdownExpanded = false

                dropdownPanel?.visibility = View.GONE
                arrow?.rotation = 0f
                btnTranslate?.text = "Translate"

                line1?.text = "Use $targetWord — you used ${matchResult.matchedClue}"
                line2?.text = matchResult.contextWord.whyExplanation
                line2?.visibility = View.VISIBLE

                if (!isBarShowing) {
                    isBarShowing = true
                    Log.i(TAG, "Bar shown: target_word='$targetWord'")
                    animateCircularReveal(show = true)
                } else {
                    Log.i(TAG, "Bar updated for new match: target_word='$targetWord'")
                }
                latinIME.requestInsetsUpdate()
            } else {
                if (!isTeluguView) {
                    line1?.text = "Use $targetWord — you used ${matchResult.matchedClue}"
                }
            }
        } else {
            if (isBarShowing) {
                isBarShowing = false
                activeTargetWord = null
                currentMatch = null
                Log.i(TAG, "Bar hidden")
                animateCircularReveal(show = false)
            }
        }
    }

    private fun animateCircularReveal(show: Boolean) {
        val root = getOrInitRoot() ?: return

        if (show) {
            root.visibility = View.VISIBLE
            root.bringToFront()
            if (root.width > 0 && root.height > 0) {
                startCircularReveal(root, true)
            } else {
                root.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        v: View?, left: Int, top: Int, right: Int, bottom: Int,
                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                    ) {
                        root.removeOnLayoutChangeListener(this)
                        if (root.width > 0 && root.height > 0) {
                            startCircularReveal(root, true)
                        }
                    }
                })
            }
        } else {
            if (root.width > 0 && root.height > 0) {
                startCircularReveal(root, false)
            } else {
                root.visibility = View.GONE
                latinIME.requestInsetsUpdate()
            }
        }
    }

    private fun startCircularReveal(root: View, show: Boolean) {
        val width = root.width
        val height = root.height
        if (width <= 0 || height <= 0) {
            if (!show) {
                root.visibility = View.GONE
                latinIME.requestInsetsUpdate()
            }
            return
        }

        val cx = height / 2
        val cy = height / 2
        val finalRadius = Math.hypot(width.toDouble(), height.toDouble()).toFloat()

        if (show) {
            try {
                val anim = ViewAnimationUtils.createCircularReveal(root, cx, cy, 0f, finalRadius)
                anim.duration = 600
                anim.interpolator = AccelerateDecelerateInterpolator()
                anim.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting circular reveal show", e)
            }
        } else {
            try {
                val anim = ViewAnimationUtils.createCircularReveal(root, cx, cy, finalRadius, 0f)
                anim.duration = 600
                anim.interpolator = AccelerateDecelerateInterpolator()
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        root.visibility = View.GONE
                        latinIME.requestInsetsUpdate()
                    }
                })
                anim.start()
            } catch (e: Exception) {
                root.visibility = View.GONE
                latinIME.requestInsetsUpdate()
            }
        }
    }

    fun isBarShowing(): Boolean {
        return isBarShowing && (rootView?.visibility == View.VISIBLE)
    }

    fun getBarHeight(): Int {
        if (!isBarShowing()) return 0
        val root = getOrInitRoot() ?: return 0
        return root.height
    }

    private fun speakWord(text: String, rate: Float) {
        if (tts == null) {
            tts = TextToSpeech(latinIME.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    tts?.language = Locale.ENGLISH
                    tts?.setSpeechRate(rate)
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ContextWordUtterance")
                }
            }
        } else if (isTtsInitialized) {
            tts?.setSpeechRate(rate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ContextWordUtterance")
        }
    }

    fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsInitialized = false
    }

    companion object {
        private const val TAG = "ContextBar"
    }
}
