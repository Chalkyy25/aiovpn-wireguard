/*
 * Copyright © 2017-2026 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aiovpn.login

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aiovpn.api.ServerDto
import com.wireguard.android.R

data class ServerUiItem(
    val server: ServerDto,
    var pingText: String = "... ms"
)

class ServerAdapter(
    private var items: List<ServerUiItem>,
    private val onServerClick: (ServerDto) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    inner class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val serverName: TextView = view.findViewById(R.id.serverName)
        val serverLocation: TextView = view.findViewById(R.id.serverLocation)
        val serverPing: TextView = view.findViewById(R.id.serverPing)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server_full, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val item = items[position]
        val server = item.server
        
        holder.serverName.text = server.label
        holder.serverLocation.text = "Region: ${server.id}"
        holder.serverPing.text = item.pingText

        holder.itemView.setOnClickListener {
            onServerClick(server)
        }

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100).start()
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ServerUiItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}