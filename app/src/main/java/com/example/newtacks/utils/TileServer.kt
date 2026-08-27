package com.example.newtacks.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File

class TileServer(private val context: Context, private val mbtilesFile: File) : NanoHTTPD(8888) {

    private var database: SQLiteDatabase? = null

    override fun start() {
        database = SQLiteDatabase.openDatabase(mbtilesFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        
        // Log Metadata to confirm format
        val cursor = database?.rawQuery("SELECT name, value FROM metadata", null)
        while (cursor?.moveToNext() == true) {
            android.util.Log.d("TileServer", "Metadata: ${cursor.getString(0)} = ${cursor.getString(1)}")
        }
        cursor?.close()
        
        super.start(SOCKET_READ_TIMEOUT, false)
    }

    override fun stop() {
        database?.close()
        super.stop()
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri // Expected format: /z/x/y.png
        val parts = uri.trim('/').split('/')
        
        if (parts.size >= 3) {
            try {
                val z = parts[0].toInt()
                val x = parts[1].toInt()
                val yRaw = parts[2].substringBefore('.').toInt()
                
                // Try TMS format first (standard for MBTiles)
                val yTMS = (1 shl z) - 1 - yRaw
                
                var cursor = database?.rawQuery(
                    "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                    arrayOf(z.toString(), x.toString(), yTMS.toString())
                )

                if (cursor == null || !cursor.moveToFirst()) {
                    cursor?.close()
                    // Try XYZ format (non-flipped)
                    cursor = database?.rawQuery(
                        "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                        arrayOf(z.toString(), x.toString(), yRaw.toString())
                    )
                }

                if (cursor != null && cursor.moveToFirst()) {
                    val blob = cursor.getBlob(0)
                    cursor.close()
                    
                    // Header check: 0x1F 0x8B is GZIP
                    val isGzip = blob.size > 2 && blob[0] == 0x1F.toByte() && blob[1] == 0x8B.toByte()

                    return if (isGzip) {
                        val response = newChunkedResponse(Response.Status.OK, "application/x-protobuf", ByteArrayInputStream(blob))
                        response.addHeader("Content-Encoding", "gzip")
                        response
                    } else {
                        newChunkedResponse(Response.Status.OK, "image/png", ByteArrayInputStream(blob))
                    }
                }
                cursor?.close()
                android.util.Log.w("TileServer", "Tile NOT found: $z/$x/$yRaw (tried TMS and XYZ)")
                cursor?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Tile not found")
    }
}