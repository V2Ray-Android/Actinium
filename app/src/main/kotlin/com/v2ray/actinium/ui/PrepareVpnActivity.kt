package com.v2ray.actinium.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle

class PrepareVpnActivity : Activity() {
    companion object {
        fun startActivity(context: Context) {
            val startIntent = Intent(context, PrepareVpnActivity::class.java)
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            context.startActivity(startIntent)
        }

        private const val REQUEST_CODE_VPN_PREPARE = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = VpnService.prepare(this)
        if (intent != null)
            startActivityForResult(intent, REQUEST_CODE_VPN_PREPARE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        overridePendingTransition(0, 0)
        finish()
    }
}