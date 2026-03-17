package com.aiovpn.login

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiovpn.api.ServerDto
import com.aiovpn.home.HomeActivity
import com.aiovpn.repo.VpnRepository
import com.aiovpn.util.PingUtil
import com.aiovpn.wireguard.WgAdapter
import com.wireguard.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ServerFilter {
    ALL,
    FASTEST,
    RECOMMENDED,
    FAVORITES
}

class ServerListActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ServerAdapter

    private val uiItems = mutableListOf<ServerUiItem>()
    private val allUiItems = mutableListOf<ServerUiItem>()

    private lateinit var navHomeContainer: View
    private lateinit var navServersContainer: View
    private lateinit var navAccountContainer: View
    private lateinit var navSettingsContainer: View
    private lateinit var sideNav: View

    private lateinit var filterAll: TextView
    private lateinit var filterFastest: TextView
    private lateinit var filterRecommended: TextView
    private lateinit var filterFavorites: TextView

    private lateinit var navTexts: List<View>
    private var isExpanded = false
    private var currentFilter = ServerFilter.ALL

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

        filterAll = findViewById(R.id.filterAll)
        filterFastest = findViewById(R.id.filterFastest)
        filterRecommended = findViewById(R.id.filterRecommended)
        filterFavorites = findViewById(R.id.filterFavorites)

        navTexts = listOf(
            findViewById(R.id.navHome),
            findViewById(R.id.navServers),
            findViewById(R.id.navAccount),
            findViewById(R.id.navSettings)
        )

        setupRecyclerView()
        bindNavigation()
        bindFilters()
        loadServers()

        recyclerView.post {
            recyclerView.requestFocus()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.clipToPadding = false
        recyclerView.clipChildren = false

        adapter = ServerAdapter(
            items = emptyList(),
            onMoveToSidebar = {
                navServersContainer.requestFocus()
            }
        ) { server ->
            connectToServer(server)
        }

        recyclerView.adapter = adapter
    }

    private fun bindFilters() {
        val chips = listOf(filterAll, filterFastest, filterRecommended, filterFavorites)

        fun updateSelection(selected: TextView) {
            chips.forEach { it.isSelected = it === selected }
        }

        filterAll.setOnClickListener {
            currentFilter = ServerFilter.ALL
            updateSelection(filterAll)
            applyFilter()
        }
        filterFastest.setOnClickListener {
            currentFilter = ServerFilter.FASTEST
            updateSelection(filterFastest)
            applyFilter()
        }
        filterRecommended.setOnClickListener {
            currentFilter = ServerFilter.RECOMMENDED
            updateSelection(filterRecommended)
            applyFilter()
        }
        filterFavorites.setOnClickListener {
            currentFilter = ServerFilter.FAVORITES
            updateSelection(filterFavorites)
            applyFilter()
        }

        chips.forEachIndexed { index, chip ->
            chip.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        recyclerView.requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (index == 0) {
                            navServersContainer.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }

        updateSelection(filterAll)
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            ServerFilter.ALL -> allUiItems
            ServerFilter.FASTEST -> allUiItems.sortedBy { parsePing(it.pingText) }.take(9)
            ServerFilter.RECOMMENDED -> allUiItems.sortedBy { parsePing(it.pingText) }.take(6)
            ServerFilter.FAVORITES -> emptyList()
        }

        uiItems.clear()
        uiItems.addAll(filtered)
        adapter.updateItems(uiItems.toList())
    }

    private fun parsePing(pingText: String): Int {
        return pingText.substringBefore(" ms").toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun bindNavigation() {
        val navContainers = listOf(
            navHomeContainer,
            navServersContainer,
            navAccountContainer,
            navSettingsContainer
        )

        navServersContainer.isSelected = true

        navContainers.forEach { container ->
            container.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    navContainers.forEach { it.isSelected = false }
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
                            navServersContainer.isSelected = true
                            toggleSidebar(false)
                        }
                    }, 50)
                }
            }

            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    filterAll.requestFocus()
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
                        // Already on this screen
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
            text.animate()
                .alpha(targetAlpha)
                .setDuration(200)
                .start()
        }
    }

    private fun loadServers() {
        lifecycleScope.launch {
            try {
                val servers = withContext(Dispatchers.IO) {
                    repo.servers()
                }

                Log.d(TAG, "Loaded servers count=${servers.size}")

                allUiItems.clear()
                allUiItems.addAll(servers.map { ServerUiItem(it) })
                uiItems.clear()
                uiItems.addAll(allUiItems)
                adapter.updateItems(uiItems.toList())

                allUiItems.forEachIndexed { index, item ->
                    launch(Dispatchers.IO) {
                        val pingText = measurePingText(item.server)

                        withContext(Dispatchers.Main) {
                            if (index in allUiItems.indices) {
                                allUiItems[index] = allUiItems[index].copy(pingText = pingText)
                                applyFilter()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load servers", e)
                Toast.makeText(
                    this@ServerListActivity,
                    "Failed to load servers: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun measurePingText(server: ServerDto): String {
        return try {
            val host = server.endpoint?.substringBefore(":")?.ifBlank { server.endpoint }
            val ping = PingUtil.measurePing(host)
            if (ping > 0) "$ping ms" else "-- ms"
        } catch (e: Exception) {
            Log.e(TAG, "Ping failed for server id=${server.id}, label=${server.label}", e)
            "-- ms"
        }
    }

    private fun connectToServer(server: ServerDto) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to server id=${server.id}, label=${server.label}")

                val config = repo.wgConfig(server.id)
                val label = server.label ?: server.name ?: "Unknown Server"

                Log.d(TAG, "WireGuard config loaded for server id=${server.id}")

                WgAdapter.connect(server.id, label, config)

                withContext(Dispatchers.Main) {
                    val intent = Intent(this@ServerListActivity, HomeActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("server_id", server.id)
                        putExtra("server_label", label)
                    }
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed for server id=${server.id}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ServerListActivity,
                        "Connection failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "ServerListActivity"
    }
}
