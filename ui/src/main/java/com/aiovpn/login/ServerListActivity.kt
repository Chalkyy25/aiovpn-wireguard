package com.aiovpn.login

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiovpn.home.HomeActivity
import com.aiovpn.repo.VpnRepository
import com.aiovpn.wireguard.WgAdapter
import com.wireguard.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerListActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository
    private var serversCache = emptyList<com.aiovpn.api.ServerDto>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aio_server_list)

        repo = VpnRepository(this)

        val list = findViewById<ListView>(R.id.aioServerList)

        lifecycleScope.launch {
            try {
                val servers = withContext(Dispatchers.IO) { repo.servers() }
                serversCache = servers

                val labels = servers.map { it.label }
                list.adapter = ArrayAdapter(
                    this@ServerListActivity,
                    android.R.layout.simple_list_item_1,
                    labels
                )

                list.setOnItemClickListener { _, _, position, _ ->
                    connectToServer(position)
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

    private fun connectToServer(position: Int) {
        val server = serversCache.getOrNull(position) ?: return

        lifecycleScope.launch {
            try {
                Toast.makeText(
                    this@ServerListActivity,
                    "Connecting to ${server.label}",
                    Toast.LENGTH_SHORT
                ).show()

                val config = withContext(Dispatchers.IO) {
                    repo.wgConfig(server.id)
                }

                // Use server ID internally for safe tunnel naming
                WgAdapter.connect(server.id, server.label, config)

                Toast.makeText(
                    this@ServerListActivity,
                    "Connected to ${server.label}",
                    Toast.LENGTH_LONG
                ).show()

                // Go back to HomeActivity after successful connection
                startActivity(Intent(this@ServerListActivity, HomeActivity::class.java))
                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@ServerListActivity,
                    e.message ?: "Connection failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}