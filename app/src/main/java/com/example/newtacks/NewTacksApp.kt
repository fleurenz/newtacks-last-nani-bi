package com.example.newtacks

import android.app.Application
import com.cloudinary.android.MediaManager

class NewtacksApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val config = hashMapOf(
            "cloud_name" to "dkuqdvofs",
            "api_key" to "599712254439336",
            "api_secret" to "oOZWsnVXETQkhhhm6aYrsZhMahc"
        )

        MediaManager.init(this, config)
    }
}