package com.aiovpn.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aiovpn.home.HomeActivity
import com.aiovpn.login.LoginActivity
import com.wireguard.android.Application
import com.wireguard.android.activity.MainActivity

class AuthGateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = Application.getVpnRepository().getTokenSync()
        val isTv = packageManager.hasSystemFeature("android.software.leanback")

        val nextIntent = when {
            token.isNullOrBlank() -> Intent(this, LoginActivity::class.java)
            isTv -> Intent(this, HomeActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }

        startActivity(nextIntent)
        finish()
        overridePendingTransition(0, 0)
    }
}