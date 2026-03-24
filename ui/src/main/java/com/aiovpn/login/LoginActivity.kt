package com.aiovpn.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiovpn.home.HomeActivity
import com.aiovpn.repo.VpnRepository
import com.aiovpn.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aio_login)

        repo = VpnRepository(this)

        val username = findViewById<EditText>(R.id.aioUsername)
        val password = findViewById<EditText>(R.id.aioPassword)
        val btn = findViewById<Button>(R.id.aioLoginBtn)

        btn.setOnClickListener {
            val u = username.text.toString().trim()
            val p = password.text.toString()

            if (u.isBlank() || p.isBlank()) {
                Toast.makeText(this, "Enter username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btn.isEnabled = false

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.login(u, p)
                    }

                    val isTv = packageManager.hasSystemFeature("android.software.leanback")

                    val next = if (isTv) {
                        Intent(this@LoginActivity, HomeActivity::class.java)
                    } else {
                        Intent(this@LoginActivity, HomeActivity::class.java)
                    }

                    startActivity(next)
                    finish()

                } catch (e: Exception) {
                    Toast.makeText(
                        this@LoginActivity,
                        e.message ?: "Login failed",
                        Toast.LENGTH_LONG
                    ).show()
                    btn.isEnabled = true
                }
            }
        }
    }
}