package com.aiovpn.login

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aiovpn.api.ServerDto
import com.aiovpn.app.R
import java.util.Locale

data class ServerUiItem(
    val server: ServerDto,
    val pingText: String = "... ms"
)

class ServerAdapter(
    private var items: List<ServerUiItem>,
    private val onMoveToSidebar: () -> Unit,
    private val onServerClick: (ServerDto) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    inner class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val serverFlag: ImageView = view.findViewById(R.id.serverFlag)
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

        holder.serverName.text = server.country?.let { code -> Locale("", code).displayCountry } ?: server.label ?: "Unknown"
        holder.serverLocation.text = server.city ?: ""
        holder.serverPing.text = item.pingText
        holder.serverFlag.setImageResource(resolveFlagRes(holder.itemView, server.country))

        holder.itemView.setOnClickListener {
            onServerClick(server)
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate()
                    .scaleX(1.03f)
                    .scaleY(1.03f)
                    .translationZ(8f)
                    .setDuration(150)
                    .start()
                view.isSelected = true
            } else {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationZ(0f)
                    .setDuration(150)
                    .start()
                view.isSelected = false
            }
        }

        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (position % 3 == 0) {
                    onMoveToSidebar()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ServerUiItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun resolveFlagRes(view: View, countryCode: String?): Int {
        val normalized = countryCode
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.length == 2 }
            ?: return R.drawable.ic_nav_servers

        val resId = view.resources.getIdentifier(
            "flag_$normalized",
            "drawable",
            view.context.packageName
        )

        return if (resId != 0) resId else R.drawable.ic_nav_servers
    }
}
