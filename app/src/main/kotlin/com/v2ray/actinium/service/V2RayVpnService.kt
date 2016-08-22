package com.v2ray.actinium.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.v2ray.actinium.event.V2RayStatusEvent
import com.v2ray.actinium.event.VpnServiceSendSelfEvent
import com.v2ray.actinium.event.VpnServiceStatusEvent
import com.v2ray.actinium.util.ConfigUtil
import com.v2ray.actinium.util.configFile

class V2RayVpnService : VpnService() {

    private lateinit var mInterface: ParcelFileDescriptor
    val fd: Int get() = mInterface.fd

    override fun onCreate() {
        super.onCreate()
        Bus.observe<V2RayStatusEvent>()
                .filter { !it.isRunning }
                .subscribe { stopSelf() }
                .registerInBus(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Bus.unregister(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Bus.send(VpnServiceSendSelfEvent(this))
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onRevoke() {
        Bus.send(VpnServiceStatusEvent(false))
        super.onRevoke()
    }

    fun setup(parameters: String) {
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

        val conf = configFile.readText()
        val dnsServers = ConfigUtil.readDnsServersFromConfig(conf, "8.8.8.8", "8.8.4.4")
        for (dns in dnsServers)
            builder.addDnsServer(dns)

        // Close the old interface since the parameters have been changed.
        try {
            mInterface.close()
        } catch (ignored: Exception) {
        }

        // Create a new interface using the builder and save the parameters.
        mInterface = builder.establish()
        Bus.send(VpnServiceStatusEvent(true))
        Log.i("VPNService", "New interface: " + parameters)
    }
}

