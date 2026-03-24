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
import com.aiovpn.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        bindNavigation()
        loadAccountData()

        logoutButton.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate()
                    .scaleX(1.04f)
                    .scaleY(1.04f)
                    .setDuration(120)
                    .start()
            } else {
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
        }
    }

    private fun loadAccountData() {
        lifecycleScope.launch {
            try {
                updateUi(
                    username = repo.getUsername(),
                    expiry = repo.getExpiry(),
                    devices = repo.getDevicesAllowed()
                )

                withContext(Dispatchers.IO) {
                    repo.refreshProfile()
                }

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

    private fun updateUi(username: String?, expiry: String?, devices: Int?) {
        val displayUser = username.orEmpty().ifBlank { "-" }
        val displayDevices = devices?.toString() ?: "-"
        val displayExpiry = formatExpiryDate(expiry)

        accountEmail.text = displayUser
        subscriptionExpiry.text = "Expires on $displayExpiry"
        accountSubtitle.text = "Manage your subscription and profile"
        devicesAllowed.text = displayDevices
    }

    private fun formatExpiryDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return "Never"

        return try {
            val instant = java.time.OffsetDateTime.parse(isoDate)
            instant.format(
                java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")
            )
        } catch (e: Exception) {
            Log.e("AccountActivity", "Failed to parse expiry date: $isoDate", e)
            isoDate
        }
    }

    private fun handleLogout() {
        lifecycleScope.launch {
            try {
                repo.logout()
            } catch (e: Exception) {
                Log.e("AccountActivity", "Logout failed", e)
            }

            val intent = Intent(this@AccountActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun bindNavigation() {
        val navContainers = listOf(
            navHomeContainer,
            navServersContainer,
            navAccountContainer,
            navSettingsContainer
        )

        navAccountContainer.isSelected = true

        navContainers.forEach { container ->
            container.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    navContainers.forEach { it.isSelected = false }
                    view.isSelected = true
                    toggleSidebar(true)

                    view.animate()
                        .scaleX(1.03f)
                        .scaleY(1.03f)
                        .setDuration(120)
                        .start()
                } else {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start()

                    view.postDelayed({
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

                    R.id.navAccountContainer -> {
                        // already here
                    }

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

        val startWidth = sideNav.width
        val targetWidth = if (expand) 240.toPx() else 84.toPx()
        val targetAlpha = if (expand) 1f else 0f

        ValueAnimator.ofInt(startWidth, targetWidth).apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val params = sideNav.layoutParams
                params.width = animator.animatedValue as Int
                sideNav.layoutParams = params
            }

            start()
        }

        navTexts.forEach { text ->
            text.animate()
                .alpha(targetAlpha)
                .setDuration(200)
                .start()
        }
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}