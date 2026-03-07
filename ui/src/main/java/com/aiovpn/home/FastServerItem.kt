package com.aiovpn.home

data class FastServerItem(
    val id: Int,
    val label: String,
    val pingText: String,
    val isAllServers: Boolean = false
)