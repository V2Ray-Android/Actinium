package com.v2ray.actinium.service

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.orhanobut.logger.Logger
import com.v2ray.actinium.event.V2RayStatusEvent
import com.v2ray.actinium.event.VpnServiceSendSelfEvent
import com.v2ray.actinium.event.VpnServiceStatusEvent
import com.v2ray.actinium.ui.BypassListActivity
import com.v2ray.actinium.ui.SettingsActivity
import com.v2ray.actinium.util.ConfigUtil
import com.v2ray.actinium.util.currConfigFile
import org.jetbrains.anko.defaultSharedPreferences
import java.util.*

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

        val conf = currConfigFile.readText()
        val dnsServers = ConfigUtil.readDnsServersFromConfig(conf, "8.8.8.8", "8.8.4.4")
        for (dns in dnsServers)
            builder.addDnsServer(dns)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                defaultSharedPreferences.getBoolean(SettingsActivity.PREF_PER_APP_PROXY, false)) {
            val blacklist = defaultSharedPreferences.getStringSet(BypassListActivity.PREF_BYPASS_LIST_SET, HashSet<String>())
            for (app in blacklist)
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
        Bus.send(VpnServiceStatusEvent(true))
        Log.i("VPNService", "New interface: " + parameters)
    }
}

