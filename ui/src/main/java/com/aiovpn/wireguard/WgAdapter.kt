package com.aiovpn.wireguard

import com.wireguard.android.Application
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader

object WgAdapter {

    /**
     * Connects to a specific server.
     * Handles disconnecting existing tunnels and waiting for the backend to clear.
     */
    suspend fun connect(serverId: Int, label: String, configText: String) = withContext(Dispatchers.IO) {
        val manager = Application.getTunnelManager()
        val config = Config.parse(BufferedReader(StringReader(configText)))
        val tunnelName = "aio_$serverId"

        // 1. Force disconnect ALL existing tunnels first
        val tunnels = manager.getTunnels()
        for (t in tunnels) {
            if (t.state != Tunnel.State.DOWN) {
                manager.setTunnelState(t, Tunnel.State.DOWN)
            }
        }

        // 2. Wait for the system to actually release the VPN interface
        // WireGuard state transitions are async; without this, the next UP command may be ignored.
        delay(1000)

        // 3. Prepare the target tunnel
        val existing = tunnels[tunnelName]
        val tunnel: ObservableTunnel = if (existing != null) {
            manager.setTunnelConfig(existing, config)
            existing
        } else {
            manager.create(tunnelName, config)
        }

        // 4. Connect
        manager.setTunnelState(tunnel, Tunnel.State.UP)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val manager = Application.getTunnelManager()
        val tunnels = manager.getTunnels()
        for (t in tunnels) {
            if (t.state != Tunnel.State.DOWN) {
                manager.setTunnelState(t, Tunnel.State.DOWN)
            }
        }
    }
}