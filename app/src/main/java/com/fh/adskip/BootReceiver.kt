package com.fh.adskip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AdSkipVpnService.prefs(context).getBoolean("enabled", false)) return
        try {
            context.startForegroundService(Intent(context, AdSkipVpnService::class.java))
        } catch (_: Exception) {}
    }
}