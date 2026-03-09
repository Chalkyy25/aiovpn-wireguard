package com.aiovpn.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiovpn.home.HomeActivity
import com.aiovpn.repo.VpnRepository
import com.aiovpn.wireguard.WgAdapter
import com.aiovpn.util.PingUtil
import com.wireguard.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ServerListActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ServerAdapter
    private var uiItems = mutableListOf<ServerUiItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aio_server_list)

        repo = VpnRepository(this)
        recyclerView = findViewById(R.id.aioServerList)
        
        setupRecyclerView()
        loadServers()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ServerAdapter(emptyList()) { server ->
            connectToServer(server)
        }
        recyclerView.adapter = adapter
    }

    private fun loadServers() {
        lifecycleScope.launch {
            try {
                val servers = withContext(Dispatchers.IO) { repo.servers() }
                
                uiItems = servers.map { ServerUiItem(it) }.toMutableList()
                adapter.updateItems(uiItems)

                // Measure pings for all servers in the list
                uiItems.forEachIndexed { index, item ->
                    launch(Dispatchers.IO) {
                        val ping = PingUtil.measurePing(item.server.endpoint)
                        withContext(Dispatchers.Main) {
                            uiItems[index] = uiItems[index].copy(pingText = "${ping} ms")
                            adapter.updateItems(uiItems.toList())
                        }
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@ServerListActivity,
                    e.message ?: "Failed to load servers",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun connectToServer(server: com.aiovpn.api.ServerDto) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = repo.wgConfig(server.id)
                WgAdapter.connect(server.id, server.label, config)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ServerListActivity, "Connection failed", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        val intent = Intent(this, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
