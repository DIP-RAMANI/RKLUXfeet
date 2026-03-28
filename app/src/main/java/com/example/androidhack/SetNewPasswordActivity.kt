package com.example.androidhack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * SetNewPasswordActivity — Allows the user to set a new password.
 *
 * Validates password strength and confirmation match,
 * then navigates to the Password Updated confirmation screen.
 */
class SetNewPasswordActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnSetPassword: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_new_password)

        btnBack = findViewById(R.id.btnBack)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSetPassword = findViewById(R.id.btnSetPassword)

        btnBack.setOnClickListener { finish() }

        btnSetPassword.setOnClickListener {
            val newPassword = etNewPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            when {
                newPassword.isEmpty() -> showToast("Please enter a new password")
                newPassword.length < 8 ->
                    showToast("Password must be at least 8 characters long")
                !isPasswordStrong(newPassword) ->
                    showToast("Password must include letters, numbers, and symbols")
                confirmPassword.isEmpty() -> showToast("Please confirm your password")
                newPassword != confirmPassword -> showToast("Passwords do not match")
                else -> {
                    // TODO: Update password via Firebase / backend
                    val intent = Intent(this, PasswordUpdatedActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    /**
     * Checks if the password includes at least one letter, one digit,
     * and one special character (symbol).
     */
    private fun isPasswordStrong(password: String): Boolean {
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        return hasLetter && hasDigit && hasSymbol
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
