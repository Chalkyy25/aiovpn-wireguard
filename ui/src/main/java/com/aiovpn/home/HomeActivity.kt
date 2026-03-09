package com.aiovpn.home

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.databinding.Observable
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiovpn.login.ServerListActivity
import com.aiovpn.repo.VpnRepository
import com.aiovpn.util.PingUtil
import com.aiovpn.wireguard.WgAdapter
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var glowView: ImageView
    private lateinit var connectButton: ImageView
    private lateinit var powerIcon: ImageView
    private lateinit var connectFocusRing: View
    private lateinit var buttonGlowView: ImageView
    private lateinit var statusPill: TextView
    private lateinit var selectedServerText: TextView
    private lateinit var fastestServersRecycler: RecyclerView

    private lateinit var navHome: TextView
    private lateinit var navServers: TextView
    private lateinit var navAccount: TextView
    private lateinit var navSettings: TextView

    private var currentVpnState: VpnUiState = VpnUiState.DISCONNECTED
    private var selectedServerId: Int? = null
    private var selectedServerLabel: String? = null

    private var tunnelCallback: Observable.OnPropertyChangedCallback? = null
    private var connectButtonStroke: GradientDrawable? = null

    enum class VpnUiState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        repo = VpnRepository(this)

        drawerLayout = findViewById(R.id.homeDrawer)
        glowView = findViewById(R.id.glowView)
        buttonGlowView = findViewById(R.id.buttonGlowView)
        connectButton = findViewById(R.id.connectButton)
        powerIcon = findViewById(R.id.powerIcon)
        connectFocusRing = findViewById(R.id.connectFocusRing)
        statusPill = findViewById(R.id.statusPill)
        selectedServerText = findViewById(R.id.selectedServerText)
        fastestServersRecycler = findViewById(R.id.fastestServersRecycler)

        navHome = findViewById(R.id.navHome)
        navServers = findViewById(R.id.navServers)
        navAccount = findViewById(R.id.navAccount)
        navSettings = findViewById(R.id.navSettings)

        connectButtonStroke = connectButton.background?.mutate() as? GradientDrawable

        setupRecycler()
        bindNavigation()
        bindConnectButton()

        setVpnState(VpnUiState.DISCONNECTED)
        selectedServerText.text = "No server selected"

        loadFastestServers()
        observeTunnelState()

        connectButton.post {
            connectButton.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUiFromManager()
    }

    override fun onDestroy() {
        super.onDestroy()
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
        listOf(navHome, navServers, navAccount, navSettings).forEach { item ->
            item.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    drawerLayout.closeDrawers()
                    connectButton.requestFocus()
                    true
                } else {
                    false
                }
            }
        }

        navHome.setOnClickListener {
            drawerLayout.closeDrawers()
        }

        navServers.setOnClickListener {
            startActivity(Intent(this, ServerListActivity::class.java))
        }

        navAccount.setOnClickListener {
            drawerLayout.closeDrawers()
        }

        navSettings.setOnClickListener {
            drawerLayout.closeDrawers()
        }
    }

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
                    drawerLayout.openDrawer(GravityCompat.START)
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
                WgAdapter.connect(serverId, label, config)

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
        fastestServersRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

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

                val initialItems = servers.take(4).map {
                    FastServerItem(it.id, it.label, "... ms", false)
                }.toMutableList().apply {
                    add(FastServerItem(-1, "All Servers", "See more", true))
                }

                val adapter = FastServerAdapter(
                    items = initialItems,
                    drawerLayout = drawerLayout,
                    onServerClick = { item ->
                        if (item.isAllServers) {
                            startActivity(Intent(this@HomeActivity, ServerListActivity::class.java))
                        } else {
                            prepareVpnAndConnect(item.id, item.label)
                        }
                    }
                )

                fastestServersRecycler.adapter = adapter

                val sortedItems = withContext(Dispatchers.IO) {
                    val pings = servers.map { server ->
                        async {
                            val pingValue = try {
                                PingUtil.measurePing(server.endpoint)
                            } catch (_: Exception) {
                                -1
                            }
                            server to pingValue
                        }
                    }.awaitAll()

                    pings.sortedBy { (_, ping) ->
                        if (ping < 0) Int.MAX_VALUE else ping
                    }.take(4).map { (server, ping) ->
                        FastServerItem(
                            id = server.id,
                            label = server.label,
                            pingText = if (ping > 0) "$ping ms" else "-- ms",
                            isAllServers = false
                        )
                    }.toMutableList().apply {
                        add(FastServerItem(-1, "All Servers", "See more", true))
                    }
                }

                adapter.updateItems(sortedItems)

            } catch (e: Exception) {
                Log.e("HomeActivity", "Failed to load fastest servers", e)
            }
        }
    }

    private fun setVpnState(state: VpnUiState) {
        currentVpnState = state

        when (state) {
            VpnUiState.DISCONNECTED -> {
                buttonGlowView.alpha = 0f
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
                buttonGlowView.alpha = 1f
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
                buttonGlowView.alpha = 1f
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

        private var pendingServerId: Int? = null
        private var pendingServerLabel: String? = null
    }
}