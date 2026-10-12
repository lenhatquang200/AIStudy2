package com.aistudy.aistudy.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase

class LoginActivity : AppCompatActivity() {

    companion object {
        const val PREFS_AUTH = "auth"
        const val KEY_USER_ID = "user_id"
    }

    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS_AUTH, MODE_PRIVATE)
        if (prefs.getLong(KEY_USER_ID, -1) != -1L) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.auth_activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        editEmail = findViewById(R.id.edit_email)
        editPassword = findViewById(R.id.edit_password)
        btnSubmit = findViewById(R.id.btn_submit)

        findViewById<View>(R.id.btn_toggle_mode).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnSubmit.setOnClickListener { login() }
    }

    private fun login() {
        val email = editEmail.text.toString().trim().lowercase()
        val password = editPassword.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.auth_error_empty_fields, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.auth_error_invalid_email, Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, R.string.auth_error_password_short, Toast.LENGTH_SHORT).show()
            return
        }
        val userDao = AppDatabase.getInstance(this).userDao()
        val user = userDao.findByEmail(email)

        if (user == null || password != user.password) {
            Toast.makeText(this, R.string.auth_error_invalid_credentials, Toast.LENGTH_SHORT).show()
            return
        }

        getSharedPreferences(PREFS_AUTH, MODE_PRIVATE)
            .edit()
            .putLong(KEY_USER_ID, user.id)
            .apply()

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
