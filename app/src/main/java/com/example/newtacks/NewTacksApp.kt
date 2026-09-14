package com.example.newtacks

import android.app.Application
import com.example.newtacks.BuildConfig
import com.cloudinary.android.MediaManager
import com.example.newtacks.utils.OfflineHelper
import com.example.newtacks.utils.TileServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import java.io.File

class StractApp : Application() {

    private var tileServer: TileServer? = null

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize MapLibre
        MapLibre.getInstance(this)
        
        // 2. Start Local Tile Server in background thread to prevent jank
        val mapPath = OfflineHelper.getLocalMapPath(this, "osm-2020-02-10-v3.11_philippines_davao-city.mbtiles")
        tileServer = TileServer(this, File(mapPath))
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                tileServer?.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val config = hashMapOf(
            "cloud_name" to BuildConfig.CLOUDINARY_NAME,
            "api_key" to BuildConfig.CLOUDINARY_API_KEY,
            "api_secret" to BuildConfig.CLOUDINARY_API_SECRET
        )

        MediaManager.init(this, config)
    }
}