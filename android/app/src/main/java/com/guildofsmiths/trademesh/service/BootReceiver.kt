package com.guildofsmiths.trademesh.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.i("BootReceiver", "Boot/package replace received — starting MeshService")
        val svc = Intent(context, MeshService::class.java)
        context.startForegroundService(svc)
    }
}
