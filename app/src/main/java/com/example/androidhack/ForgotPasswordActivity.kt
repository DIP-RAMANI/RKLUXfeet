package com.example.androidhack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * ForgotPasswordActivity — Password reset request screen.
 *
 * Accepts the user's email and (stub) sends password reset instructions.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etEmail: EditText
    private lateinit var btnSendReset: Button
    private lateinit var tvRememberPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        btnBack = findViewById(R.id.btnBack)
        etEmail = findViewById(R.id.etEmail)
        btnSendReset = findViewById(R.id.btnSendReset)
        tvRememberPassword = findViewById(R.id.tvRememberPassword)

        btnBack.setOnClickListener { finish() }

        btnSendReset.setOnClickListener {
            val email = etEmail.text.toString().trim()
            when {
                email.isEmpty() -> showToast("Please enter your email")
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    showToast("Please enter a valid email")
                else -> {
                    // TODO: Send reset email via Firebase / backend
                    showToast("Reset instructions sent to $email")
                    startActivity(Intent(this, SetNewPasswordActivity::class.java))
                }
            }
        }

        tvRememberPassword.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
