/*
 * Copyright © 2017-2026 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aiovpn.home

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.Observable
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiovpn.login.ServerListActivity
import com.aiovpn.repo.VpnRepository
import com.aiovpn.util.ConnectButtonAnimator
import com.aiovpn.util.PingUtil
import com.aiovpn.util.SettingsStore
import com.aiovpn.wireguard.WgAdapter
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository
    private lateinit var settingsStore: SettingsStore

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var glowView: ImageView
    private lateinit var connectButton: ImageView
    private lateinit var powerIcon: ImageView
    private lateinit var connectFocusRing: View
    private lateinit var buttonGlowView: ImageView
    private lateinit var statusPill: TextView
    private lateinit var selectedServerText: TextView
    private lateinit var fastestServersRecycler: RecyclerView

    private lateinit var sideNav: View
    private lateinit var navHomeContainer: View
    private lateinit var navServersContainer: View
    private lateinit var navAccountContainer: View
    private lateinit var navSettingsContainer: View

    private lateinit var navTexts: List<View>
    private var isExpanded = false

    private var currentVpnState: VpnUiState = VpnUiState.DISCONNECTED
    private var selectedServerId: Int? = null
    private var selectedServerLabel: String? = null

    private var tunnelCallback: Observable.OnPropertyChangedCallback? = null
    private var connectButtonStroke: GradientDrawable? = null

    private lateinit var connectButtonAnimator: ConnectButtonAnimator
    private lateinit var fastServerAdapter: FastServerAdapter
    private var fastServerLookup: Map<Int, String> = emptyMap()

    enum class VpnUiState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        repo = VpnRepository(this)
        settingsStore = SettingsStore(this)

        drawerLayout = findViewById(R.id.homeDrawer)
        glowView = findViewById(R.id.glowView)
        buttonGlowView = findViewById(R.id.buttonGlowView)
        connectButton = findViewById(R.id.connectButton)
        powerIcon = findViewById(R.id.powerIcon)
        connectFocusRing = findViewById(R.id.connectFocusRing)
        statusPill = findViewById(R.id.statusPill)
        selectedServerText = findViewById(R.id.selectedServerText)
        fastestServersRecycler = findViewById(R.id.fastestServersRecycler)

        sideNav = findViewById(R.id.sideNav)
        navHomeContainer = findViewById(R.id.navHomeContainer)
        navServersContainer = findViewById(R.id.navServersContainer)
        navAccountContainer = findViewById(R.id.navAccountContainer)
        navSettingsContainer = findViewById(R.id.navSettingsContainer)

        navTexts = listOf(
            findViewById(R.id.navHome),
            findViewById(R.id.navServers),
            findViewById(R.id.navAccount),
            findViewById(R.id.navSettings)
        )

        connectButtonStroke = connectButton.background?.mutate() as? GradientDrawable
        connectButtonAnimator = ConnectButtonAnimator(buttonGlowView)

        setupRecycler()
        bindNavigation()
        bindConnectButton()

        setVpnState(VpnUiState.DISCONNECTED)
        
        // Restore last server from settings
        lifecycleScope.launch {
            selectedServerId = settingsStore.lastServerIdFlow.first()
            selectedServerLabel = settingsStore.lastServerLabelFlow.first()
            selectedServerText.text = selectedServerLabel ?: "No server selected"
            
            // Auto-connect if enabled
            if (settingsStore.autoConnectFlow.first() && selectedServerId != null && selectedServerLabel != null) {
                handleConnectButtonClick()
            }
        }

        handleIntent(intent)
        observeTunnelState()

        connectButton.post {
            connectButton.requestFocus()
        }

        // DELAYED LOAD: Don't choke the startup transition
        lifecycleScope.launch {
            delay(1500)
            loadFastestServers()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val serverId = intent?.getIntExtra("server_id", -1)?.takeIf { it != -1 }
        val serverLabel = intent?.getStringExtra("server_label")

        if (serverId != null && serverLabel != null) {
            selectedServerId = serverId
            selectedServerLabel = serverLabel
            selectedServerText.text = serverLabel
            
            // Save as last used server
            lifecycleScope.launch {
                settingsStore.saveLastServer(serverId, serverLabel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUiFromManager()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectButtonAnimator.stop()
        tunnelCallback?.let { callback ->
            try {
                Application.getTunnelManager().removeOnPropertyChangedCallback(callback)
            } catch (_: Exception) {
            }
        }
        tunnelCallback = null
    }

    private fun observeTunnelState() {
        val callback = object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                runOnUiThread { updateUiFromManager() }
            }
        }

        tunnelCallback = callback
        Application.getTunnelManager().addOnPropertyChangedCallback(callback)
    }

    private fun updateUiFromManager() {
        lifecycleScope.launch {
            try {
                val tunnels = Application.getTunnelManager().getTunnels()
                val activeTunnel = tunnels.firstOrNull { it.state != Tunnel.State.DOWN }

                withContext(Dispatchers.Main) {
                    if (activeTunnel == null) {
                        setVpnState(VpnUiState.DISCONNECTED)
                        selectedServerText.text = selectedServerLabel ?: "No server selected"
                    } else {
                        val state = if (activeTunnel.state == Tunnel.State.UP) {
                            VpnUiState.CONNECTED
                        } else {
                            VpnUiState.CONNECTING
                        }

                        setVpnState(state)

                        val fallbackLabel = activeTunnel.name.removePrefix("aio_").let { "Server $it" }
                        selectedServerText.text = selectedServerLabel ?: fallbackLabel
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Failed to update tunnel UI", e)
            }
        }
    }

    private fun bindNavigation() {
        val navContainers = listOf(navHomeContainer, navServersContainer, navAccountContainer, navSettingsContainer)

        navHomeContainer.isSelected = true

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
                    connectButton.requestFocus()
                    true
                } else {
                    false
                }
            }

            container.setOnClickListener {
                when (container.id) {
                    R.id.navHomeContainer -> {
                        // Already here
                    }
                    R.id.navServersContainer -> {
                        startActivity(Intent(this, ServerListActivity::class.java))
                    }
                    R.id.navAccountContainer -> {
                        startActivity(Intent(this, com.aiovpn.home.AccountActivity::class.java))
                    }
                    R.id.navSettingsContainer -> {
                        startActivity(Intent(this, com.aiovpn.home.SettingsActivity::class.java))
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

    private fun bindConnectButton() {
        connectButton.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                connectFocusRing.animate().alpha(0.85f).setDuration(120).start()
                v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
                powerIcon.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
            } else {
                connectFocusRing.animate().alpha(0f).setDuration(120).start()
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                powerIcon.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }

        connectButton.setOnClickListener {
            handleConnectButtonClick()
        }

        connectButton.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    navHomeContainer.requestFocus()
                    true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    fastestServersRecycler.post {
                        val firstCard =
                            fastestServersRecycler.layoutManager?.findViewByPosition(0)
                                ?: fastestServersRecycler.getChildAt(0)

                        firstCard?.requestFocus()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun handleConnectButtonClick() {
        if (currentVpnState != VpnUiState.DISCONNECTED) {
            disconnectCurrentTunnel()
            return
        }

        val serverId = selectedServerId
        val label = selectedServerLabel

        if (serverId != null && !label.isNullOrBlank()) {
            prepareVpnAndConnect(serverId, label)
        } else {
            startActivity(Intent(this, ServerListActivity::class.java))
        }
    }

    private fun disconnectCurrentTunnel() {
        setVpnState(VpnUiState.DISCONNECTED)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                WgAdapter.disconnect()
            } catch (e: Exception) {
                Log.e("HomeActivity", "Disconnect failed", e)
            }
        }
    }

    private fun prepareVpnAndConnect(serverId: Int, label: String) {
        val permissionIntent = VpnService.prepare(this)

        if (permissionIntent != null) {
            pendingServerId = serverId
            pendingServerLabel = label
            startActivityForResult(permissionIntent, REQUEST_VPN_PERMISSION)
        } else {
            connectToSpecificServer(serverId, label)
        }
    }

    private fun connectToSpecificServer(serverId: Int, label: String) {
        selectedServerId = serverId
        selectedServerLabel = label
        selectedServerText.text = label
        setVpnState(VpnUiState.CONNECTING)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                WgAdapter.disconnect()
                delay(600)

                val config = repo.wgConfig(serverId)
                
                // Read Kill Switch setting
                val killSwitch = settingsStore.killSwitchFlow.first()
                
                WgAdapter.connect(serverId, label, config, killSwitch)

            } catch (e: Exception) {
                Log.e("HomeActivity", "Connection failed", e)
                withContext(Dispatchers.Main) {
                    setVpnState(VpnUiState.DISCONNECTED)
                    Toast.makeText(
                        this@HomeActivity,
                        "Connection failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_VPN_PERMISSION) return

        val serverId = pendingServerId
        val label = pendingServerLabel

        pendingServerId = null
        pendingServerLabel = null

        if (resultCode == Activity.RESULT_OK) {
            if (serverId != null && !label.isNullOrBlank()) {
                connectToSpecificServer(serverId, label)
            } else {
                startActivity(Intent(this, ServerListActivity::class.java))
            }
        } else {
            Toast.makeText(this, "VPN permission is required to connect", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecycler() {
        fastServerAdapter = FastServerAdapter(
            items = listOf(FastServerItem(-1, "All Servers", null, "See more", null, true)),
            onMoveToSidebar = {
                navHomeContainer.requestFocus()
            },
            onServerClick = { item ->
                if (item.isAllServers) {
                    startActivity(Intent(this@HomeActivity, ServerListActivity::class.java))
                } else {
                    val fullLabel = fastServerLookup[item.id] ?: item.label
                    prepareVpnAndConnect(item.id, fullLabel)
                }
            }
        )

        fastestServersRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        fastestServersRecycler.adapter = fastServerAdapter
        fastestServersRecycler.clipToPadding = false
        fastestServersRecycler.clipChildren = false
        fastestServersRecycler.setHasFixedSize(true)
        fastestServersRecycler.isFocusable = true
        fastestServersRecycler.isFocusableInTouchMode = true
        fastestServersRecycler.descendantFocusability = RecyclerView.FOCUS_AFTER_DESCENDANTS
    }

    private fun loadFastestServers() {
        lifecycleScope.launch {
            try {
                val servers = withContext(Dispatchers.IO) { repo.servers() }
                fastServerLookup = servers.associate { server ->
                    server.id to (server.label ?: displayNameFor(server.country, fallback = "Unknown"))
                }

                // Show basic list first
                val initialItems = servers.take(4).map { server ->
                    FastServerItem(
                        id = server.id,
                        label = displayNameFor(server.country, server.label ?: ""),
                        cityName = server.city,
                        pingText = "... ms",
                        countryCode = server.country,
                        isAllServers = false
                    )
                }.toMutableList().apply {
                    add(FastServerItem(-1, "All Servers", null, "See more", null, true))
                }

                fastServerAdapter.updateItems(initialItems)

                // Limit concurrency strictly for TV hardware
                val semaphore = Semaphore(2)

                val sortedItems = withContext(Dispatchers.IO) {
                    val pings = servers.take(12).map { server -> // Don't ping more than 12 at once
                        async {
                            semaphore.withPermit {
                                val pingValue = try {
                                    PingUtil.measurePing(server.endpoint ?: "")
                                } catch (_: Exception) {
                                    -1
                                }
                                server to pingValue
                            }
                        }
                    }.awaitAll()

                    val bestServers = pings.sortedBy { (_, ping) ->
                        if (ping < 0) Int.MAX_VALUE else ping
                    }.take(4)

                    bestServers.map { (server, ping) ->
                        FastServerItem(
                            id = server.id,
                            label = displayNameFor(server.country, server.label ?: "Unknown"),
                            cityName = server.city,
                            pingText = if (ping > 0) "$ping ms" else "-- ms",
                            countryCode = server.country,
                            isAllServers = false
                        )
                    }.toMutableList().apply {
                        add(FastServerItem(-1, "All Servers", null, "See more", null, true))
                    }
                }

                fastServerAdapter.updateItems(sortedItems)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load fastest servers", e)
            }
        }
    }

    private fun displayNameFor(countryCode: String?, fallback: String): String {
        return countryCode
            ?.takeIf { it.length == 2 }
            ?.let { Locale("", it).displayCountry }
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun setVpnState(state: VpnUiState) {
        currentVpnState = state

        when (state) {
            VpnUiState.DISCONNECTED -> {
                connectButtonAnimator.stop()
                glowView.animate()
                    .alpha(0f)
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(180)
                    .start()

                powerIcon.imageTintList = ColorStateList.valueOf(0xFFD1D5DB.toInt())

                statusPill.text = "Disconnected"
                statusPill.setBackgroundResource(R.drawable.pill_disconnected)
            }

            VpnUiState.CONNECTING -> {
                buttonGlowView.setImageResource(R.drawable.button_glow_purple)
                connectButtonAnimator.startConnectingAnimation()

                glowView.setImageResource(R.drawable.glow_purple)
                glowView.scaleX = 0.92f
                glowView.scaleY = 0.92f
                glowView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .start()

                powerIcon.imageTintList = ColorStateList.valueOf(0xFFB44CFF.toInt())

                statusPill.text = "Connecting"
                statusPill.setBackgroundResource(R.drawable.pill_connecting)
            }

            VpnUiState.CONNECTED -> {
                buttonGlowView.setImageResource(R.drawable.button_glow_blue)
                connectButtonAnimator.setStable(1.0f)

                glowView.setImageResource(R.drawable.glow_blue)
                glowView.scaleX = 0.92f
                glowView.scaleY = 0.92f
                glowView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .start()

                powerIcon.imageTintList = ColorStateList.valueOf(0xFF4F7BFF.toInt())

                statusPill.text = "Connected"
                statusPill.setBackgroundResource(R.drawable.pill_connected)
            }
        }
    }

    companion object {
        private const val REQUEST_VPN_PERMISSION = 1001
        private const val TAG = "HomeActivity"

        private var pendingServerId: Int? = null
        private var pendingServerLabel: String? = null
    }
}
