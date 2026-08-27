package com.example.newtacks

import android.app.Application
import com.cloudinary.android.MediaManager
import com.example.newtacks.utils.OfflineHelper
import com.example.newtacks.utils.TileServer
import org.maplibre.android.MapLibre
import java.io.File

class NewtacksApp : Application() {

    private var tileServer: TileServer? = null

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize MapLibre
        MapLibre.getInstance(this)
        
        // 2. Start Local Tile Server
        val mapPath = OfflineHelper.getLocalMapPath(this, "osm-2020-02-10-v3.11_philippines_davao-city.mbtiles")
        tileServer = TileServer(this, File(mapPath))
        try {
            tileServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val config = hashMapOf(
            "cloud_name" to "dkuqdvofs",
            "api_key" to "599712254439336",
            "api_secret" to "oOZWsnVXETQkhhhm6aYrsZhMahc"
        )

        MediaManager.init(this, config)
    }
}