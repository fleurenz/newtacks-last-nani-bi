package com.example.newtacks.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.View
import com.example.newtacks.chatbot.presentation.ui.ChatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs

object ChatbotUtils {

    @SuppressLint("ClickableViewAccessibility")
    fun setupChatbot(context: Context, fab: FloatingActionButton, role: String) {
        
        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f
        val clickDragTolerance = 10f // Threshold to differentiate click vs drag

        fab.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = view.x
                    startY = view.y
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX + dX
                    val newY = event.rawY + dY
                    
                    // Constrain within parent bounds
                    val parent = view.parent as View
                    val maxX = parent.width - view.width.toFloat()
                    val maxY = parent.height - view.height.toFloat()

                    view.x = newX.coerceIn(0f, maxX)
                    view.y = newY.coerceIn(0f, maxY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val endX = view.x
                    val endY = view.y
                    
                    // Differentiate between click and drag
                    if (abs(endX - startX) < clickDragTolerance && abs(endY - startY) < clickDragTolerance) {
                        // It's a click
                        val intent = Intent(context, ChatActivity::class.java)
                        intent.putExtra("USER_ROLE", role)
                        context.startActivity(intent)
                    } else {
                        // Snap to nearest side (Chathead style)
                        snapToEdge(view)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(view: View) {
        val parent = view.parent as View
        val centerX = parent.width / 2
        val viewCenterX = view.x + (view.width / 2)
        
        val targetX = if (viewCenterX < centerX) {
            16f * view.context.resources.displayMetrics.density // Left margin
        } else {
            parent.width.toFloat() - view.width - (16f * view.context.resources.displayMetrics.density) // Right margin
        }

        view.animate()
            .x(targetX)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }
}
