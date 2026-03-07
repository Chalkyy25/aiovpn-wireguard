package com.aiovpn.wireguard

import com.wireguard.android.Application
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader

object WgAdapter {

    suspend fun connect(serverId: Int, label: String, configText: String) = withContext(Dispatchers.IO) {

        val manager = Application.getTunnelManager()
        val config = Config.parse(BufferedReader(StringReader(configText)))

        // Safe WireGuard tunnel name
        val tunnelName = "aio_$serverId"

        val tunnels = manager.getTunnels()
        val existing = tunnels[tunnelName]

        val tunnel: ObservableTunnel = if (existing != null) {
            manager.setTunnelConfig(existing, config)
            existing
        } else {
            manager.create(tunnelName, config)
        }

        // Disconnect any other UP tunnels
        for (t in tunnels) {
            if (t.name != tunnelName && t.state == Tunnel.State.UP) {
                manager.setTunnelState(t, Tunnel.State.DOWN)
            }
        }

        // Connect selected tunnel
        manager.setTunnelState(tunnel, Tunnel.State.UP)
    }
}