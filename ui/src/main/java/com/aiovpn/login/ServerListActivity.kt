package com.aiovpn.login

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiovpn.home.HomeActivity
import com.aiovpn.repo.VpnRepository
import com.aiovpn.util.PingUtil
import com.aiovpn.wireguard.WgAdapter
import com.wireguard.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerListActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ServerAdapter
    private var uiItems = mutableListOf<ServerUiItem>()

    private lateinit var navHomeContainer: View
    private lateinit var navServersContainer: View
    private lateinit var navAccountContainer: View
    private lateinit var navSettingsContainer: View
    private lateinit var sideNav: View
    
    private lateinit var navTexts: List<View>
    private var isExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aio_server_list)

        repo = VpnRepository(this)
        recyclerView = findViewById(R.id.aioServerList)
        
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

        setupRecyclerView()
        bindNavigation()
        loadServers()
        
        // Initial focus
        recyclerView.post {
            recyclerView.requestFocus()
        }
    }

    private fun setupRecyclerView() {
        // Using a 3-column grid for better TV flow
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = ServerAdapter(
            items = emptyList(),
            onMoveToSidebar = {
                navServersContainer.requestFocus()
            }
        ) { server ->
            connectToServer(server)
        }
        recyclerView.adapter = adapter
        
        recyclerView.clipToPadding = false
        recyclerView.clipChildren = false
    }

    private fun bindNavigation() {
        val navContainers = listOf(navHomeContainer, navServersContainer, navAccountContainer, navSettingsContainer)
        
        // Highlight Servers as the current active page
        navServersContainer.isSelected = true

        navContainers.forEach { container ->
            container.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    // Hide the persistent "current page" highlight while focus is inside the sidebar
                    navContainers.forEach { it.isSelected = false }
                    
                    toggleSidebar(true)
                    v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    v.postDelayed({
                        // If focus has completely left the sidebar, restore the "Servers" page highlight and collapse
                        if (navContainers.none { it.hasFocus() }) {
                            navServersContainer.isSelected = true
                            toggleSidebar(false)
                        }
                    }, 50)
                }
            }

            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    recyclerView.requestFocus()
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
                        // Already here
                    }
                    R.id.navAccountContainer -> {
                        startActivity(Intent(this, com.aiovpn.home.AccountActivity::class.java))
                        finish()
                    }
                    R.id.navSettingsContainer -> {
                        startActivity(Intent(this, com.aiovpn.home.SettingsActivity::class.java))
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

    private fun loadServers() {
        lifecycleScope.launch {
            try {
                val servers = withContext(Dispatchers.IO) { repo.servers() }
                
                uiItems = servers.map { ServerUiItem(it) }.toMutableList()
                adapter.updateItems(uiItems)

                // Async ping measurement
                uiItems.forEachIndexed { index, item ->
                    launch(Dispatchers.IO) {
                        val ping = try { PingUtil.measurePing(item.server.endpoint ?: "") } catch (e: Exception) { -1 }
                        withContext(Dispatchers.Main) {
                            if (index < uiItems.size) {
                                uiItems[index] = uiItems[index].copy(pingText = if (ping > 0) "${ping} ms" else "-- ms")
                                adapter.updateItems(uiItems.toList())
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(this@ServerListActivity, "Failed to load servers", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun connectToServer(server: com.aiovpn.api.ServerDto) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = repo.wgConfig(server.id)
                val label = server.label ?: "Unknown Server"
                WgAdapter.connect(server.id, label, config)
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@ServerListActivity, HomeActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    // PASS THE SELECTION DATA
                    intent.putExtra("server_id", server.id)
                    intent.putExtra("server_label", label)
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ServerListActivity, "Connection failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
