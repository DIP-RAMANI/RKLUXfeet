package com.example.androidhack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

/**
 * PasswordUpdatedActivity — Success confirmation screen.
 *
 * Shown after the user successfully sets a new password.
 * Navigates back to the Login screen.
 */
class PasswordUpdatedActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnBackToLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_updated)

        btnBack = findViewById(R.id.btnBack)
        btnBackToLogin = findViewById(R.id.btnBackToLogin)

        btnBack.setOnClickListener {
            navigateToLogin()
        }

        btnBackToLogin.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        // Clear the entire back stack so the user can't go back to the password flow
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}
