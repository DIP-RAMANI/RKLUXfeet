package com.example.androidhack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * MainActivity — Main / Splash screen (RKLUXfeet)
 *
 * Displays the animated app branding, Login, and Register entry points.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnLogin: Button = findViewById(R.id.btnLogin)
        val btnRegister: Button = findViewById(R.id.btnRegister)
        
        val ivSplashLogo: ImageView = findViewById(R.id.ivSplashLogo)
        val tvSplashTitle: TextView = findViewById(R.id.tvSplashTitle)
        val tvSplashTagline: TextView = findViewById(R.id.tvSplashTagline)
        val tvSplashFooter: TextView = findViewById(R.id.tvSplashFooter)

        // 1. Logo Animation Sequence (Smooth Scale & Fade)
        ivSplashLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1200)
            .start()

        // 2. Typography Stagger Fade (Delayed Entries)
        tvSplashTitle.animate()
            .alpha(1f)
            .setStartDelay(300)
            .setDuration(800)
            .start()

        tvSplashTagline.animate()
            .alpha(1f)
            .setStartDelay(500)
            .setDuration(800)
            .start()
            
        tvSplashFooter.animate()
            .alpha(1f)
            .setStartDelay(800)
            .setDuration(800)
            .start()

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        // If user is already logged in, skip the splash/login flow entirely
        if (FirebaseAuth.getInstance().currentUser != null) {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}