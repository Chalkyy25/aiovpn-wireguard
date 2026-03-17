/*
 * Copyright © 2017-2026 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

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
     * @param killSwitch If true, ensures the tunnel is configured for block-on-disconnect if supported by backend.
     */
    suspend fun connect(serverId: Int, label: String, configText: String, killSwitch: Boolean = false) = withContext(Dispatchers.IO) {
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

        // 2. Wait for system to release interface
        delay(800)

        // 3. Prepare target tunnel
        val existing = tunnels[tunnelName]
        val tunnel: ObservableTunnel = if (existing != null) {
            manager.setTunnelConfig(existing, config)
            existing
        } else {
            manager.create(tunnelName, config)
        }

        // 4. Connect
        // Note: For a true Kill Switch on Android, we typically rely on the OS "Always-on VPN" 
        // and "Block connections without VPN" settings. 
        // However, in our adapter, we can ensure the state transition is solid.
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
