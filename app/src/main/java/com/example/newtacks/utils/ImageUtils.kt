package com.example.newtacks.utils

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import coil.load
import com.example.newtacks.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

object ImageUtils {

    fun showFullscreenImage(context: Context, imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) return

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_fullscreen_image)

        val ivFullscreen = dialog.findViewById<ImageView>(R.id.ivFullscreen)
        val btnClose = dialog.findViewById<FloatingActionButton>(R.id.btnClose)

        ivFullscreen.load(imageUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_image_placeholder)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}