package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.net.URLEncoder

object DeviceActionExecutor {

    fun executeAction(context: Context, type: String, query: String): Result<String> {
        return try {
            val intent = when (type.lowercase()) {
                "timer" -> {
                    val seconds = query.filter { it.isDigit() }.toIntOrNull()?.coerceIn(5, 86400) ?: 300
                    Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "MYRA Timer")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }
                }
                "alarm" -> {
                    Intent(AlarmClock.ACTION_SHOW_ALARMS)
                }
                "url" -> {
                    val url = if (query.startsWith("http://") || query.startsWith("https://")) {
                        query
                    } else {
                        "https://$query"
                    }
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                }
                "search" -> {
                    val encoded = URLEncoder.encode(query.ifBlank { "MYRA AI" }, "UTF-8")
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
                }
                "email" -> {
                    val target = if (query.contains("@")) "mailto:$query" else "mailto:"
                    Intent(Intent.ACTION_SENDTO, Uri.parse(target)).apply {
                        if (!query.contains("@") && query.isNotBlank()) {
                            putExtra(Intent.EXTRA_SUBJECT, query)
                        }
                    }
                }
                "dial" -> {
                    val digits = query.filter { it.isDigit() || it == '+' }
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
                }
                "settings" -> {
                    val action = when (query.lowercase()) {
                        "wifi", "internet" -> Settings.ACTION_WIFI_SETTINGS
                        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                        else -> Settings.ACTION_SETTINGS
                    }
                    Intent(action)
                }
                else -> {
                    val encoded = URLEncoder.encode(query.ifBlank { "MYRA" }, "UTF-8")
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Result.success("Launched $type action")
        } catch (e: Exception) {
            Result.failure(IllegalStateException("No app available to handle this action: ${e.localizedMessage}"))
        }
    }
}
