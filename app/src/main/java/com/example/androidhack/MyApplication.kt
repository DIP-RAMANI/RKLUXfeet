package com.example.androidhack

import android.app.Application
import android.util.Log
import com.cloudinary.android.MediaManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            val config = HashMap<String, String>()
            config["cloud_name"] = "dx7dfwcfl"
            MediaManager.init(this, config)
            Log.d("Cloudinary", "Initialized successfully")
        } catch (e: Exception) {
            Log.e("Cloudinary", "Error initializing Cloudinary: ${e.message}")
        }
    }
}
