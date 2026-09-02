package com.example.newtacks.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.MotionEvent
import com.example.newtacks.chatbot.presentation.ui.ChatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.pow
import kotlin.math.sqrt

object ChatbotUtils {

    @SuppressLint("ClickableViewAccessibility")
    fun setupChatbot(activity: Activity, fab: FloatingActionButton?, role: String) {
        if (fab == null) return
        
        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f
        val clickThreshold = 10 // pixels

        fab.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    startX = event.rawX
                    startY = event.rawY
                    view.animate().scaleX(1.1f).scaleY(1.1f).alpha(1.0f).setDuration(100).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    view.y = event.rawY + dY
                    view.x = event.rawX + dX
                }
                MotionEvent.ACTION_UP -> {
                    val endX = event.rawX
                    val endY = event.rawY
                    val distance = sqrt((endX - startX).toDouble().pow(2.0) + (endY - startY).toDouble().pow(2.0))

                    if (distance < clickThreshold) {
                        // Launch Chat
                        val intent = Intent(activity, ChatActivity::class.java)
                        intent.putExtra("USER_ROLE", role)
                        activity.startActivity(intent)
                    } else {
                        // Snap to edges
                        val screenWidth = activity.resources.displayMetrics.widthPixels
                        val finalX = if (view.x + view.width / 2 < screenWidth / 2) {
                            16f // Snap to left
                        } else {
                            (screenWidth - view.width - 16).toFloat() // Snap to right
                        }

                        view.animate()
                            .x(finalX)
                            .scaleX(0.8f) // Shrink effect
                            .scaleY(0.8f)
                            .alpha(0.6f)  // Transparent effect
                            .setDuration(300)
                            .start()
                    }
                }
            }
            true
        }
    }
}