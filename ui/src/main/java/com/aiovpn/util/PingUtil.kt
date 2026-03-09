package com.aiovpn.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress

object PingUtil {

    suspend fun measurePing(host: String): Int = withContext(Dispatchers.IO) {
        try {
            val cleanHost = host
                .replace("https://", "")
                .replace("http://", "")
                .substringBefore(":")
                .trim()

            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 2 $cleanHost")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("time=") == true) {
                    val timeStr = line!!
                        .substringAfter("time=")
                        .substringBefore(" ms")
                        .trim()

                    return@withContext timeStr.toDoubleOrNull()?.toInt() ?: -1
                }
            }

            val start = System.currentTimeMillis()
            if (InetAddress.getByName(cleanHost).isReachable(2000)) {
                return@withContext (System.currentTimeMillis() - start).toInt()
            }
        } catch (_: Exception) {
        }

        -1
    }
}