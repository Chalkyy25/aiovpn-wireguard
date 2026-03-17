/*
 * Copyright © 2017-2026 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aiovpn.home

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiovpn.login.ServerListActivity
import com.aiovpn.util.SettingsStore
import com.wireguard.android.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsStore: SettingsStore

    private lateinit var sideNav: View
    private lateinit var navHomeContainer: View
    private lateinit var navServersContainer: View
    private lateinit var navAccountContainer: View
    private lateinit var navSettingsContainer: View

    private lateinit var killSwitchItem: View
    private lateinit var autoConnectItem: View
    private lateinit var splitTunnelingItem: View
    
    private lateinit var killSwitchStatus: TextView
    private lateinit var autoConnectStatus: TextView
    private lateinit var splitTunnelingStatus: TextView

    private lateinit var navTexts: List<View>
    private var isExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsStore = SettingsStore(this)

        sideNav = findViewById(R.id.sideNav)
        navHomeContainer = findViewById(R.id.navHomeContainer)
        navServersContainer = findViewById(R.id.navServersContainer)
        navAccountContainer = findViewById(R.id.navAccountContainer)
        navSettingsContainer = findViewById(R.id.navSettingsContainer)

        killSwitchItem = findViewById(R.id.settingKillSwitch)
        autoConnectItem = findViewById(R.id.settingAutoConnect)
        splitTunnelingItem = findViewById(R.id.settingSplitTunneling)
        
        killSwitchStatus = findViewById(R.id.killSwitchStatus)
        autoConnectStatus = findViewById(R.id.autoConnectStatus)
        splitTunnelingStatus = findViewById(R.id.splitTunnelingStatus)

        navTexts = listOf(
            findViewById(R.id.navHome),
            findViewById(R.id.navServers),
            findViewById(R.id.navAccount),
            findViewById(R.id.navSettings)
        )

        bindNavigation()
        bindSettings()
        observeSettings()
    }

    private fun bindSettings() {
        killSwitchItem.setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.killSwitchFlow.first()
                settingsStore.setKillSwitch(!current)
            }
        }

        autoConnectItem.setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.autoConnectFlow.first()
                settingsStore.setAutoConnect(!current)
            }
        }
        
        splitTunnelingItem.setOnClickListener {
            startActivity(Intent(this, SplitTunnelingActivity::class.java))
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsStore.killSwitchFlow.collect { enabled ->
                killSwitchStatus.text = if (enabled) "On" else "Off"
                killSwitchStatus.setTextColor(if (enabled) 0xFF4ADE80.toInt() else 0xFF9CA3AF.toInt())
            }
        }

        lifecycleScope.launch {
            settingsStore.autoConnectFlow.collect { enabled ->
                autoConnectStatus.text = if (enabled) "On" else "Off"
                autoConnectStatus.setTextColor(if (enabled) 0xFF4ADE80.toInt() else 0xFF9CA3AF.toInt())
            }
        }
        
        lifecycleScope.launch {
            settingsStore.excludedAppsFlow.collect { apps ->
                val count = apps.size
                splitTunnelingStatus.text = if (count == 1) "1 App" else "$count Apps"
            }
        }
    }

    private fun bindNavigation() {
        val navContainers = listOf(navHomeContainer, navServersContainer, navAccountContainer, navSettingsContainer)

        navSettingsContainer.isSelected = true

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
                            navHomeContainer.isSelected = true
                            toggleSidebar(false)
                        }
                    }, 50)
                }
            }

            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    killSwitchItem.requestFocus()
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
                        startActivity(Intent(this, AccountActivity::class.java))
                        finish()
                    }
                    R.id.navSettingsContainer -> {}
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
