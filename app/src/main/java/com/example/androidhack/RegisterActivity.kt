package com.example.androidhack

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import androidx.activity.result.contract.ActivityResultContracts

/**
 * RegisterActivity — New user registration screen.
 * Collects Full Name, Email, Password, Confirm Password, Terms Agreement.
 * Features password strength indicator + Confirm Password validation.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnRegister: Button
    private lateinit var tvLogin: TextView
    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var cbTerms: CheckBox
    private lateinit var layoutGoogleRegister: LinearLayout

    // Password Strength Bars
    private lateinit var strengthBar1: View
    private lateinit var strengthBar2: View
    private lateinit var strengthBar3: View

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account?.idToken != null) {
                firebaseAuthWithGoogle(account.idToken!!)
            } else {
                showToast("Google token missing. Try again.")
            }
        } catch (e: ApiException) {
            showToast("Google Sign-Up Error: ${e.statusCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnBack              = findViewById(R.id.btnBack)
        btnRegister          = findViewById(R.id.btnRegister)
        tvLogin              = findViewById(R.id.tvLogin)
        etFullName           = findViewById(R.id.etFullName)
        etEmail              = findViewById(R.id.etEmail)
        etPassword           = findViewById(R.id.etPassword)
        etConfirmPassword    = findViewById(R.id.etConfirmPassword)
        cbTerms              = findViewById(R.id.cbTerms)
        layoutGoogleRegister = findViewById(R.id.layoutGoogleRegister)
        strengthBar1         = findViewById(R.id.strengthBar1)
        strengthBar2         = findViewById(R.id.strengthBar2)
        strengthBar3         = findViewById(R.id.strengthBar3)

        // Password strength watcher
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordStrength(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnBack.setOnClickListener { finish() }

        layoutGoogleRegister.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        btnRegister.setOnClickListener {
            val fullName        = etFullName.text.toString().trim()
            val email           = etEmail.text.toString().trim()
            val password        = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            when {
                fullName.isEmpty() ->
                    showToast("Please enter your full name")
                email.isEmpty() ->
                    showToast("Please enter your email")
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    showToast("Please enter a valid email")
                password.length < 8 ->
                    showToast("Password must be at least 8 characters")
                password != confirmPassword ->
                    showToast("Passwords do not match")
                !cbTerms.isChecked ->
                    showToast("Please agree to the Terms & Privacy Policy")
                else -> {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) {
                                    val userMap = mapOf(
                                        "name" to fullName,
                                        "email" to email,
                                        "role" to "user",
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("users").document(user.uid)
                                        .set(userMap)
                                        .addOnCompleteListener {
                                            showToast("Account created successfully!")
                                            val intent = Intent(this, HomeActivity::class.java)
                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            startActivity(intent)
                                            finish()
                                        }
                                }
                            } else {
                                showToast("Registration failed: ${task.exception?.message}")
                            }
                        }
                }
            }
        }

        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun updatePasswordStrength(password: String) {
        val strength = when {
            password.length >= 10 && password.any { it.isUpperCase() } &&
            password.any { it.isDigit() }                               -> 3 // Strong
            password.length >= 8                                         -> 2 // Medium
            password.isNotEmpty()                                        -> 1 // Weak
            else                                                         -> 0
        }
        val colors = listOf("#F44336", "#FF9800", "#4CAF50") // Red, Orange, Green
        strengthBar1.setBackgroundColor(if (strength >= 1) Color.parseColor(colors[strength - 1]) else Color.parseColor("#E0E0E0"))
        strengthBar2.setBackgroundColor(if (strength >= 2) Color.parseColor(colors[strength - 1]) else Color.parseColor("#E0E0E0"))
        strengthBar3.setBackgroundColor(if (strength >= 3) Color.parseColor(colors[strength - 1]) else Color.parseColor("#E0E0E0"))
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("users").document(user.uid).get()
                            .addOnSuccessListener { doc ->
                                val userMap = mutableMapOf<String, Any>(
                                    "name" to (user.displayName ?: "No Name"),
                                    "email" to (user.email ?: ""),
                                    "profileImageUrl" to (user.photoUrl?.toString() ?: "")
                                )
                                if (!doc.exists()) {
                                    userMap["role"] = "user"
                                    userMap["timestamp"] = System.currentTimeMillis()
                                }
                                db.collection("users").document(user.uid)
                                    .set(userMap, com.google.firebase.firestore.SetOptions.merge())
                                    .addOnCompleteListener {
                                        showToast("Google Sign-Up successful!")
                                        val intent = Intent(this@RegisterActivity, HomeActivity::class.java)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        finish()
                                    }
                            }
                            .addOnFailureListener {
                                showToast("Failed to fetch user data")
                            }
                    }
                } else {
                    showToast("Google Auth failed: ${task.exception?.message}")
                }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
