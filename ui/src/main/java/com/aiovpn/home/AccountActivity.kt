/*
 * Copyright © 2017-2026 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aiovpn.home

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiovpn.login.LoginActivity
import com.aiovpn.login.ServerListActivity
import com.aiovpn.repo.VpnRepository
import com.wireguard.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AccountActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository

    private lateinit var sideNav: View
    private lateinit var navHomeContainer: View
    private lateinit var navServersContainer: View
    private lateinit var navAccountContainer: View
    private lateinit var navSettingsContainer: View

    private lateinit var accountSubtitle: TextView
    private lateinit var subscriptionExpiry: TextView
    private lateinit var accountEmail: TextView
    private lateinit var devicesAllowed: TextView
    private lateinit var logoutButton: Button

    private lateinit var navTexts: List<View>
    private var isExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        repo = VpnRepository(this)

        sideNav = findViewById(R.id.sideNav)
        navHomeContainer = findViewById(R.id.navHomeContainer)
        navServersContainer = findViewById(R.id.navServersContainer)
        navAccountContainer = findViewById(R.id.navAccountContainer)
        navSettingsContainer = findViewById(R.id.navSettingsContainer)

        accountSubtitle = findViewById(R.id.accountSubtitle)
        subscriptionExpiry = findViewById(R.id.subscriptionExpiry)
        accountEmail = findViewById(R.id.accountEmail)
        devicesAllowed = findViewById(R.id.devicesAllowed)
        logoutButton = findViewById(R.id.logoutButton)

        navTexts = listOf(
            findViewById(R.id.navHome),
            findViewById(R.id.navServers),
            findViewById(R.id.navAccount),
            findViewById(R.id.navSettings)
        )

        loadAccountData()
        bindNavigation()

        logoutButton.setOnClickListener {
            handleLogout()
        }
    }

    private fun loadAccountData() {
        lifecycleScope.launch {
            try {
                // 1. Show cached data immediately
                updateUi(
                    username = repo.getUsername(),
                    expiry = repo.getExpiry(),
                    devices = repo.getDevicesAllowed()
                )

                // 2. Fetch fresh data from backend
                withContext(Dispatchers.IO) {
                    repo.refreshProfile()
                }

                // 3. Update UI with fresh data
                updateUi(
                    username = repo.getUsername(),
                    expiry = repo.getExpiry(),
                    devices = repo.getDevicesAllowed()
                )
            } catch (e: Exception) {
                Log.e("AccountActivity", "Failed to refresh profile", e)
            }
        }
    }

    private fun updateUi(username: String?, expiry: String?, devices: String?) {
        val displayUser = username.orEmpty().ifBlank { "-" }
        val displayDevices = devices.orEmpty().ifBlank { "-" }
        val displayExpiry = formatExpiryDate(expiry)

        accountEmail.text = displayUser
        subscriptionExpiry.text = "Expires on $displayExpiry"
        accountSubtitle.text = "Manage your subscription and profile"
        devicesAllowed.text = displayDevices
    }

    private fun formatExpiryDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return "-"
        return try {
            // Your API format: 2027-10-12T20:06:48.000000Z
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(isoDate)
            if (date != null) outputFormat.format(date) else isoDate
        } catch (e: Exception) {
            isoDate // Fallback to raw string if parsing fails
        }
    }

    private fun handleLogout() {
        lifecycleScope.launch {
            repo.logout()
            val intent = Intent(this@AccountActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun bindNavigation() {
        val navContainers = listOf(navHomeContainer, navServersContainer, navAccountContainer, navSettingsContainer)

        navAccountContainer.isSelected = true

        navContainers.forEach { container ->
            container.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    navContainers.forEach { it.isSelected = false }
                    toggleSidebar(true)
                    v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    v.postDelayed({
                        if (navContainers.none { it.hasFocus() }) {
                            navAccountContainer.isSelected = true
                            toggleSidebar(false)
                        }
                    }, 50)
                }
            }

            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    logoutButton.requestFocus()
                    true
                } else {
                    false
                }
            }

            container.setOnClickListener {
                when (container.id) {
                    R.id.navHomeContainer -> {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }
                    R.id.navServersContainer -> {
                        startActivity(Intent(this, ServerListActivity::class.java))
                        finish()
                    }
                    R.id.navAccountContainer -> {}
                    R.id.navSettingsContainer -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }

    private fun toggleSidebar(expand: Boolean) {
        if (isExpanded == expand) return
        isExpanded = expand

        val targetWidth = if (expand) 240.toPx() else 84.toPx()
        val targetAlpha = if (expand) 1f else 0f

        val widthAnimator = ValueAnimator.ofInt(sideNav.width, targetWidth)
        widthAnimator.addUpdateListener { animator ->
            val params = sideNav.layoutParams
            params.width = animator.animatedValue as Int
            sideNav.layoutParams = params
        }

        widthAnimator.duration = 250
        widthAnimator.interpolator = AccelerateDecelerateInterpolator()
        widthAnimator.start()

        navTexts.forEach { text ->
            text.animate().alpha(targetAlpha).setDuration(200).start()
        }
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()
}
