package com.example.newtacks.utils

import android.os.SystemClock
import android.view.View

/**
 * Utility and extension functions to prevent click spamming during low/slow network conditions.
 */
object SingleClickUtils {
    private var lastGlobalClickTime: Long = 0L
    private const val DEFAULT_INTERVAL = 800L

    /**
     * Checks if a click occurred too quickly after the previous click globally.
     */
    fun isFastDoubleClick(interval: Long = DEFAULT_INTERVAL): Boolean {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastGlobalClickTime < interval) {
            return true
        }
        lastGlobalClickTime = currentTime
        return false
    }
}

/**
 * Extension function on View to set an OnClickListener that debounces fast clicks.
 * Ignores any subsequent click fired within [debounceInterval] milliseconds.
 */
fun View.setOnSingleClickListener(debounceInterval: Long = 800L, onSingleClick: (View) -> Unit) {
    var lastClickTime = 0L
    setOnClickListener { v ->
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastClickTime >= debounceInterval) {
            lastClickTime = currentTime
            onSingleClick(v)
        }
    }
}
