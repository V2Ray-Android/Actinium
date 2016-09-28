package com.v2ray.actinium.service

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.hwangjr.rxbus.annotation.Subscribe
import com.orhanobut.logger.Logger
import com.v2ray.actinium.event.V2RayStatusEvent
import com.v2ray.actinium.event.VpnServiceSendSelfEvent
import com.v2ray.actinium.event.VpnServiceStatusEvent
import com.v2ray.actinium.extension.rxBus
import com.v2ray.actinium.util.ConfigUtil
import java.io.File

class V2RayVpnService : VpnService() {

    private lateinit var mInterface: ParcelFileDescriptor
    val fd: Int get() = mInterface.fd

    override fun onCreate() {
        super.onCreate()
        rxBus.register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        rxBus.unregister(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        rxBus.post(VpnServiceSendSelfEvent(this))
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onRevoke() {
        rxBus.post(VpnServiceStatusEvent(false))
        super.onRevoke()
    }


    @Subscribe
    fun updateV2RayStatus(event: V2RayStatusEvent) {
        if (!event.isRunning)
            stopSelf()
    }

    fun setup(parameters: String, configPath: String, bypassList: Array<String>?) {
        // If the old interface has exactly the same parameters, use it!
        // Configure a builder while parsing the parameters.
        val builder = Builder()

        for (parameter in parameters.split(" ")) {
            val fields = parameter.split(",")
            when (fields[0][0]) {
                'm' -> builder.setMtu(java.lang.Short.parseShort(fields[1]).toInt())
                'a' -> builder.addAddress(fields[1], Integer.parseInt(fields[2]))
                'r' -> builder.addRoute(fields[1], Integer.parseInt(fields[2]))
                's' -> builder.addSearchDomain(fields[1])
            }

        }

        val conf = File(configPath).readText()
        val dnsServers = ConfigUtil.readDnsServersFromConfig(conf, "8.8.8.8", "8.8.4.4")
        for (dns in dnsServers)
            builder.addDnsServer(dns)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                bypassList != null) {
            for (app in bypassList)
                try {
                    builder.addDisallowedApplication(app)
                } catch (e: PackageManager.NameNotFoundException) {
                    Logger.d(e)
                }
        }

        // Close the old interface since the parameters have been changed.
        try {
            mInterface.close()
        } catch (ignored: Exception) {
        }

        // Create a new interface using the builder and save the parameters.
        mInterface = builder.establish()
        rxBus.post(VpnServiceStatusEvent(true))
        Log.i("VPNService", "New interface: " + parameters)
    }
}

