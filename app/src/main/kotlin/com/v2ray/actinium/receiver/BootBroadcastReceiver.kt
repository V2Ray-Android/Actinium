package com.v2ray.actinium.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.actinium.service.V2RayVpnService
import com.v2ray.actinium.ui.SettingsActivity
import org.jetbrains.anko.defaultSharedPreferences

/**
 * Created by Takay on 2016/8/16.
 */
class BootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (ctx.defaultSharedPreferences.getBoolean(SettingsActivity.PREF_START_ON_BOOT, false)) {
            V2RayVpnService.startV2Ray(ctx)
        }
    }
}