package com.v2ray.actinium.service

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.support.v7.app.AppCompatActivity
import com.v2ray.actinium.aidl.IV2RayService
import com.v2ray.actinium.dto.VpnNetworkInfo
import com.v2ray.actinium.extra.IV2RayServiceCallbackStub
import com.v2ray.actinium.ui.PrepareVpnActivity

@TargetApi(Build.VERSION_CODES.N)
class ActiniumTileService : TileService() {

    var bgService: IV2RayService? = null

    val conn = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            bgService?.unregisterCallback(serviceCallback)
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val service1 = IV2RayService.Stub.asInterface(service)
            bgService = service1
            service1.registerCallback(serviceCallback)
            serviceCallback.onStateChanged(service1.isRunning)

        }
    }

    val serviceCallback = object : IV2RayServiceCallbackStub(this) {
        override fun onNetworkInfoUpdated(info: VpnNetworkInfo?) {
        }

        override fun onStateChanged(isRunning: Boolean) {
            qsTile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            qsTile.updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()

        val intent = Intent(this.applicationContext, V2RayVpnService::class.java)
        bindService(intent, conn, AppCompatActivity.BIND_AUTO_CREATE)
    }

    override fun onStopListening() {
        super.onStopListening()

        bgService?.unregisterCallback(serviceCallback)
        unbindService(conn)
    }

    override fun onClick() {
        super.onClick()
        when (qsTile.state) {
            Tile.STATE_INACTIVE -> {
                val intent = VpnService.prepare(this)
                if (intent == null)
                    V2RayVpnService.startV2Ray(this)
                else
                    PrepareVpnActivity.startActivity(this)
            }
            Tile.STATE_ACTIVE -> bgService?.stopV2Ray()
        }
    }
}