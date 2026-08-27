package com.example.newtacks.utils

import android.content.Context
import android.util.Log
import org.maplibre.android.offline.OfflineManager
import java.io.File
import java.io.FileOutputStream

object OfflineHelper {
    private const val TAG = "OfflineHelper"

    fun getLocalMapPath(context: Context, assetName: String): String {
        val dbFile = File(context.filesDir, assetName)
        if (!dbFile.exists()) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return dbFile.absolutePath
    }

    fun mergeOfflineMap(context: Context, assetName: String) {
        val dbFile = File(context.filesDir, assetName)
        
        // 1. Copy asset to internal storage if it doesn't exist
        if (!dbFile.exists()) {
            try {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied $assetName to internal storage")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying asset: ${e.message}")
                return
            }
        }

        // 2. Merge the .mbtiles file into MapLibre's internal database
        OfflineManager.getInstance(context).mergeOfflineRegions(
            dbFile.absolutePath,
            object : OfflineManager.MergeOfflineRegionsCallback {
                override fun onMerge(regions: Array<org.maplibre.android.offline.OfflineRegion>?) {
                    Log.d(TAG, "Successfully merged offline map tiles!")
                    dbFile.delete() // Clean up the temp file after merge
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Error merging offline map: $error")
                }
            }
        )
    }
}