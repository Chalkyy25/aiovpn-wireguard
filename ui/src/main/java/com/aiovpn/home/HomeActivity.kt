package com.aiovpn.home

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.aiovpn.login.ServerListActivity
import com.aiovpn.repo.VpnRepository
import com.wireguard.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var glowView: ImageView
    private lateinit var connectButton: ImageView
    private lateinit var statusPill: TextView
    private lateinit var selectedServerText: TextView
    private lateinit var fastestServersRecycler: RecyclerView

    private lateinit var navHome: TextView
    private lateinit var navServers: TextView
    private lateinit var navAccount: TextView
    private lateinit var navSettings: TextView

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
        connectButton = findViewById(R.id.connectButton)
        statusPill = findViewById(R.id.statusPill)
        selectedServerText = findViewById(R.id.selectedServerText)
        fastestServersRecycler = findViewById(R.id.fastestServersRecycler)

        navHome = findViewById(R.id.navHome)
        navServers = findViewById(R.id.navServers)
        navAccount = findViewById(R.id.navAccount)
        navSettings = findViewById(R.id.navSettings)

        bindNavigation()
        bindConnectButton()
        setupRecycler()

        setVpnState(VpnUiState.DISCONNECTED)
        selectedServerText.text = "No server selected"

        loadFastestServers()
    }

    private fun bindNavigation() {
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
        connectButton.setOnClickListener {
            startActivity(Intent(this, ServerListActivity::class.java))
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
                    val firstCard = fastestServersRecycler.layoutManager?.findViewByPosition(0)
                        ?: fastestServersRecycler.getChildAt(0)

                    if (firstCard != null) {
                        firstCard.requestFocus()
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }
        }
    }

    private fun setupRecycler() {
        fastestServersRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        fastestServersRecycler.clipToPadding = false
        fastestServersRecycler.clipChildren = false
        fastestServersRecycler.setHasFixedSize(true)

        LinearSnapHelper().attachToRecyclerView(fastestServersRecycler)
    }

    private fun loadFastestServers() {
        lifecycleScope.launch {
            try {
                val servers = withContext(Dispatchers.IO) {
                    repo.servers()
                }

                val items = mutableListOf<FastServerItem>()

                servers.take(4).forEach { server ->
                    items.add(
                        FastServerItem(
                            id = server.id,
                            label = server.label,
                            pingText = "-- ms",
                            isAllServers = false
                        )
                    )
                }

                items.add(
                    FastServerItem(
                        id = -1,
                        label = "All Servers",
                        pingText = "See more",
                        isAllServers = true
                    )
                )

                fastestServersRecycler.adapter = FastServerAdapter(
                    items = items,
                    drawerLayout = drawerLayout,
                    onServerClick = { item ->
                        startActivity(Intent(this@HomeActivity, ServerListActivity::class.java))
                    },
                )

            } catch (e: Exception) {
                selectedServerText.text = e.message ?: "Failed to load servers"
            }
        }
    }

    private fun setVpnState(state: VpnUiState) {
        when (state) {
            VpnUiState.DISCONNECTED -> {
                glowView.alpha = 0f
                connectButton.setColorFilter(0xFFD9DDE4.toInt())
                statusPill.text = "Disconnected"
                statusPill.setBackgroundResource(R.drawable.pill_disconnected)
            }

            VpnUiState.CONNECTING -> {
                glowView.setImageResource(R.drawable.glow_purple)
                glowView.alpha = 1f
                connectButton.setColorFilter(0xFFB44CFF.toInt())
                statusPill.text = "Connecting"
                statusPill.setBackgroundResource(R.drawable.pill_connecting)
            }

            VpnUiState.CONNECTED -> {
                glowView.setImageResource(R.drawable.glow_blue)
                glowView.alpha = 1f
                connectButton.setColorFilter(0xFF4F7BFF.toInt())
                statusPill.text = "Connected"
                statusPill.setBackgroundResource(R.drawable.pill_connected)
            }
        }
    }
}