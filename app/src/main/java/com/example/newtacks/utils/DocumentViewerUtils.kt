package com.example.newtacks.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

/**
 * Utility to safely open documents, PDFs, resumes, and certificates across the app.
 */
object DocumentViewerUtils {

    fun openDocument(context: Context, url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, "No document URL available", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanUrl = url.trim()
        val lowerUrl = cleanUrl.lowercase()

        // Check if URL is an image
        val isImage = lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") ||
                lowerUrl.contains(".png") || lowerUrl.contains(".webp") ||
                lowerUrl.contains(".gif")

        if (isImage) {
            ImageUtils.showFullscreenImage(context, cleanUrl)
            return
        }

        // It's a PDF, DOC, or Cloudinary raw document
        // Attempt 1: Direct ACTION_VIEW intent (opens in system default PDF Reader or Browser)
        try {
            val directUri = Uri.parse(cleanUrl)
            val directIntent = Intent(Intent.ACTION_VIEW, directUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (lowerUrl.contains(".pdf")) {
                    setDataAndType(directUri, "application/pdf")
                }
            }

            if (directIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(directIntent)
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Attempt 2: Direct browser intent without MIME restriction
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            return
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Attempt 3: Google Docs Viewer with properly URL-encoded URL
        try {
            val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
            val googleDocsUrl = "https://docs.google.com/viewer?url=$encodedUrl"
            val docsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(googleDocsUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(docsIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Unable to open document. Please install a PDF reader or browser.", Toast.LENGTH_LONG).show()
        }
    }
}
