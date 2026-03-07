package com.aiovpn.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiovpn.home.HomeActivity
import com.aiovpn.login.LoginActivity
import com.aiovpn.repo.VpnRepository
import com.wireguard.android.activity.MainActivity
import kotlinx.coroutines.launch

class AuthGateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = VpnRepository(this)

        lifecycleScope.launch {

            val token = repo.getToken()
            val isTv = packageManager.hasSystemFeature("android.software.leanback")

            Log.d("AIOVPN", "Gate token=${token?.take(10)} tv=$isTv")

            // If user not logged in → Login
            if (token.isNullOrBlank()) {
                startActivity(Intent(this@AuthGateActivity, LoginActivity::class.java))
                finish()
                return@launch
            }

            // Logged in → go to Home
            val next = if (isTv) {
                Intent(this@AuthGateActivity, HomeActivity::class.java)
            } else {
                Intent(this@AuthGateActivity, MainActivity::class.java)
            }

            startActivity(next)
            finish()
        }
    }
}