package com.aistudy.aistudy.auth

import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
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
import com.aistudy.aistudy.database.User

class RegisterActivity : AppCompatActivity() {

    private lateinit var editFullName: EditText
    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.auth_activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        editFullName = findViewById(R.id.edit_full_name)
        editEmail = findViewById(R.id.edit_email)
        editPassword = findViewById(R.id.edit_password)
        btnSubmit = findViewById(R.id.btn_submit)

        findViewById<View>(R.id.btn_toggle_mode).setOnClickListener { finish() }

        btnSubmit.setOnClickListener { register() }
    }

    private fun register() {
        val fullName = editFullName.text.toString().trim()
        val email = editEmail.text.toString().trim().lowercase()
        val password = editPassword.text.toString()

        if (fullName.isEmpty()) {
            Toast.makeText(this, R.string.auth_error_name_required, Toast.LENGTH_SHORT).show()
            return
        }
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
        if (userDao.findByEmail(email) != null) {
            Toast.makeText(this, R.string.auth_error_email_taken, Toast.LENGTH_SHORT).show()
            return
        }

        val user = User()
        user.fullName = fullName
        user.email = email
        user.password = password

        try {
            val userId = userDao.insert(user)
            getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
                .edit()
                .putLong(LoginActivity.KEY_USER_ID, userId)
                .apply()

            Toast.makeText(this, R.string.auth_success_register, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: SQLiteConstraintException) {
            Toast.makeText(this, R.string.auth_error_email_taken, Toast.LENGTH_SHORT).show()
        }
    }
}
