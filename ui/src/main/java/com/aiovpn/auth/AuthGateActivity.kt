/*
 * Copyright © 2017-2026 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aiovpn.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiovpn.home.HomeActivity
import com.aiovpn.login.LoginActivity
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.activity.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthGateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            // 1. Yield to system immediately
            delay(500) 

            // 2. Use the singleton repository from Application
            val result = withContext(Dispatchers.IO) {
                try {
                    val repo = Application.getVpnRepository()
                    val token = repo.getToken()
                    val isTv = packageManager.hasSystemFeature("android.software.leanback")
                    Triple(token, isTv, true)
                } catch (e: Exception) {
                    Log.e("AIOVPN", "Gate check failed", e)
                    Triple(null, false, false)
                }
            }

            val (token, isTv, _) = result

            if (token.isNullOrBlank()) {
                startActivity(Intent(this@AuthGateActivity, LoginActivity::class.java))
            } else {
                val next = if (isTv) {
                    Intent(this@AuthGateActivity, HomeActivity::class.java)
                } else {
                    Intent(this@AuthGateActivity, MainActivity::class.java)
                }
                startActivity(next)
            }
            
            finish()
        }
    }
}
