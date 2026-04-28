package com.example.androidhack

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cloudinary.android.MediaManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class MyApplication : Application() {
    private val appLaunchTime = Timestamp.now()

    override fun onCreate() {
        super.onCreate()

        // Initialize Cloudinary for image uploads
        try {
            val config = HashMap<String, String>()
            config["cloud_name"] = "dx7dfwcfl"
            MediaManager.init(this, config)
            Log.d("Cloudinary", "Initialized successfully")
        } catch (e: Exception) {
            Log.e("Cloudinary", "Error initializing Cloudinary: ${e.message}")
        }

        // Create FCM notification channel for order status updates
        MyFirebaseMessagingService.createNotificationChannel(this)

        // Setup real-time listener for admin notifications
        setupNotificationListener()
    }

    private fun setupNotificationListener() {
        val db = FirebaseFirestore.getInstance()
        db.collection("notifications")
            .whereGreaterThan("sentAt", appLaunchTime)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("MyApplication", "Listen failed.", e)
                    return@addSnapshotListener
                }

                for (dc in snapshots!!.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val title = dc.document.getString("title") ?: "New Notification"
                        val message = dc.document.getString("message") ?: ""
                        showLocalNotification(title, message)
                    }
                }
            }
    }

    private fun showLocalNotification(title: String, message: String) {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MyFirebaseMessagingService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cart) // Use an existing icon like cart or logo
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
