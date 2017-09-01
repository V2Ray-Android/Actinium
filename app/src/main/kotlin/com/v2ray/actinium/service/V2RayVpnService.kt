package com.v2ray.actinium.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.VpnService
import android.os.*
import android.support.v7.app.NotificationCompat
import android.util.Log
import com.github.pwittchen.reactivenetwork.library.Connectivity
import com.github.pwittchen.reactivenetwork.library.ReactiveNetwork
import com.orhanobut.logger.Logger
import com.v2ray.actinium.R
import com.v2ray.actinium.aidl.IV2RayServiceCallback
import com.v2ray.actinium.defaultDPreference
import com.v2ray.actinium.dto.VpnNetworkInfo
import com.v2ray.actinium.extension.broadcastAll
import com.v2ray.actinium.extension.loadVpnNetworkInfo
import com.v2ray.actinium.extension.saveVpnNetworkInfo
import com.v2ray.actinium.extension.toSpeedString
import com.v2ray.actinium.extra.IV2RayServiceStub
import com.v2ray.actinium.ui.MainActivity
import com.v2ray.actinium.ui.PerAppProxyActivity
import com.v2ray.actinium.ui.SettingsActivity
import com.v2ray.actinium.util.ConfigUtil
import com.v2ray.actinium.util.currConfigFile
import com.v2ray.actinium.util.currConfigName
import libv2ray.Libv2ray
import libv2ray.V2RayCallbacks
import libv2ray.V2RayVPNServiceSupportsSet
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class V2RayVpnService : VpnService() {
    companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
        const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
        const val ACTION_STOP_V2RAY = "com.v2ray.actinium.action.STOP_V2RAY"

        fun startV2Ray(context: Context) {
            val intent = Intent(context.applicationContext, V2RayVpnService::class.java)
            context.startService(intent)
        }
    }

    private val v2rayPoint = Libv2ray.newV2RayPoint()
    private val v2rayCallback = V2RayCallback()
    private var connectivitySubscription: Subscription? = null

    private val handler = Handler()
    private val updateNetworkInfoCallback = object : Runnable {
        override fun run() {
            vpnNetworkInfo?.let {
                lastNetworkInfo?.let { last ->
                    if (defaultDPreference.getPrefBoolean(SettingsActivity.PREF_FOREGROUND_SERVICE, false)) {
                        val speed = it - last
                        showNotification("${speed.rxByte.toSpeedString()} ↓ ${speed.txByte.toSpeedString()} ↑")
                    }
                }
                lastNetworkInfo = it
                serviceCallbacks.broadcastAll { it.onNetworkInfoUpdated(lastNetworkInfo) }
            }
            handler.postDelayed(this, 1000)
        }
    }
    private var lastNetworkInfo: VpnNetworkInfo? = null

    private lateinit var configContent: String

    private val stopV2RayReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            stopV2Ray()
        }
    }

    var serviceCallbacks = RemoteCallbackList<IV2RayServiceCallback>()

    val binder = object : IV2RayServiceStub(this) {
        override fun isRunning(): Boolean {
            return v2rayPoint.isRunning
                    && prepare(this@V2RayVpnService) == null
        }

        override fun stopV2Ray() {
            this@V2RayVpnService.stopV2Ray()
        }

        override fun registerCallback(cb: IV2RayServiceCallback?) {
            cb?.let {
                serviceCallbacks.register(it)
            }
        }

        override fun unregisterCallback(cb: IV2RayServiceCallback?) {
            cb?.let {
                serviceCallbacks.unregister(it)
            }
        }

        override fun onPrefForegroundServiceChanged(isEnabled: Boolean) {
            if (!isEnabled)
                cancelNotification()
        }

        override fun onTransact(code: Int, data: Parcel?, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.LAST_CALL_TRANSACTION) {
                onRevoke()
                return true
            }

            return super.onTransact(code, data, reply, flags)
        }
    }

    override fun onBind(intent: Intent) = binder

    private lateinit var mInterface: ParcelFileDescriptor
    val fd: Int get() = mInterface.fd

    override fun onCreate() {
        super.onCreate()

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        v2rayPoint.packageName = packageName
    }

    override fun onRevoke() {
        stopV2Ray()
    }

    fun setup(parameters: String) {
        // If the old interface has exactly the same parameters, use it!
        // Configure a builder while parsing the parameters.
        val builder = Builder()

        parameters.split(" ")
                .map { it.split(",") }
                .forEach {
                    when (it[0][0]) {
                        'm' -> builder.setMtu(java.lang.Short.parseShort(it[1]).toInt())
                        'a' -> builder.addAddress(it[1], Integer.parseInt(it[2]))
                        'r' -> builder.addRoute(it[1], Integer.parseInt(it[2]))
                        's' -> builder.addSearchDomain(it[1])
                    }
                }

        builder.setSession(currConfigName)

        val dnsServers = ConfigUtil.readDnsServersFromConfig(configContent, "8.8.8.8", "8.8.4.4")
        for (dns in dnsServers)
            builder.addDnsServer(dns)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                defaultDPreference.getPrefBoolean(SettingsActivity.PREF_PER_APP_PROXY, false)) {
            val apps = defaultDPreference.getPrefStringSet(PerAppProxyActivity.PREF_PER_APP_PROXY_SET, null)
            val bypassApps = defaultDPreference.getPrefBoolean(PerAppProxyActivity.PREF_BYPASS_APPS, false)
            apps?.forEach {
                try {
                    if (bypassApps)
                        builder.addDisallowedApplication(it)
                    else
                        builder.addAllowedApplication(it)
                } catch (e: PackageManager.NameNotFoundException) {
                    Logger.d(e)
                }
            }

        }

        // Close the old interface since the parameters have been changed.
        try {
            mInterface.close()
        } catch (ignored: Exception) {
        }

        // Create a new interface using the builder and save the parameters.
        mInterface = builder.establish()
        Log.i("VPNService", "New interface: " + parameters)

        handler.postDelayed(updateNetworkInfoCallback, 1000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startV2ray()

        return super.onStartCommand(intent, flags, startId)
    }

    private fun vpnCheckIsReady() {
        val prepare = VpnService.prepare(this)

        if (prepare != null) {
            return
        }

        v2rayPoint.vpnSupportReady()
        serviceCallbacks.broadcastAll { it.onStateChanged(true) }
    }

    private fun startV2ray() {
        if (!v2rayPoint.isRunning) {

            registerReceiver(stopV2RayReceiver, IntentFilter(ACTION_STOP_V2RAY))

            configContent = currConfigFile.readText()

            val autoRestart = defaultDPreference
                    .getPrefStringSet(SettingsActivity.PREF_AUTO_RESTART_SET, emptySet())

            if (autoRestart.contains("kcp") && ConfigUtil.isKcpConfig(configContent)
                    || autoRestart.contains("tcp"))
                connectivitySubscription = ReactiveNetwork.observeNetworkConnectivity(this.applicationContext)
                        .subscribeOn(Schedulers.io())
                        .skip(1)
                        .filter(Connectivity.hasState(NetworkInfo.State.CONNECTED))
                        .throttleWithTimeout(3, TimeUnit.SECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            if (v2rayPoint.isRunning)
                                v2rayPoint.networkInterrupted()
                        }

            v2rayPoint.callbacks = v2rayCallback
            v2rayPoint.setVpnSupportSet(v2rayCallback)
            v2rayPoint.configureFile = "V2Ray_internal/ConfigureFileContent"
            v2rayPoint.configureFileContent = configContent
            v2rayPoint.runLoop()
        }

        if (defaultDPreference.getPrefBoolean(SettingsActivity.PREF_FOREGROUND_SERVICE, false))
            showNotification()
    }

    private fun stopV2Ray() {
        handler.removeCallbacks(updateNetworkInfoCallback)
        val configName = currConfigName
        val emptyInfo = VpnNetworkInfo()
        val info = loadVpnNetworkInfo(configName, emptyInfo)!! + (lastNetworkInfo ?: emptyInfo)
        saveVpnNetworkInfo(configName, info)

        if (v2rayPoint.isRunning) {
            v2rayPoint.stopLoop()
        }

        unregisterReceiver(stopV2RayReceiver)
        connectivitySubscription?.let {
            it.unsubscribe()
            connectivitySubscription = null
        }

        serviceCallbacks.broadcastAll { it.onStateChanged(false) }
        cancelNotification()
        stopSelf()
    }

    private fun showNotification(text: String = "0 Bytes/s ↓ 0 Bytes/s ↑") {
        val startMainIntent = Intent(applicationContext, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(applicationContext,
                NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val stopV2RayIntent = Intent(ACTION_STOP_V2RAY)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(applicationContext,
                NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(applicationContext)
                .setSmallIcon(R.drawable.ic_action_logo)
                .setContentTitle(currConfigName)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(contentPendingIntent)
                .addAction(R.drawable.ic_close_grey_800_24dp,
                        getString(R.string.notification_action_stop_v2ray),
                        stopV2RayPendingIntent)
                .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        stopForeground(true)
    }

    private val vpnNetworkInfo: VpnNetworkInfo?
        get() = FileInputStream("/proc/net/dev").bufferedReader().use {
            val prefix = "tun0:"
            while (true) {
                val line = it.readLine().trim()
                if (line.startsWith(prefix)) {
                    val numbers = line.substring(prefix.length).split(' ')
                            .filter(String::isNotEmpty)
                            .map(String::toLong)
                    if (numbers.size > 10)
                        return VpnNetworkInfo(numbers[0], numbers[1], numbers[8], numbers[9])
                    break
                }
            }
            return null
        }

    private inner class V2RayCallback : V2RayCallbacks, V2RayVPNServiceSupportsSet {
        override fun shutdown() = 0L

        override fun getVPNFd() = this@V2RayVpnService.fd.toLong()

        override fun prepare(): Long {
            vpnCheckIsReady()
            return 1
        }

        override fun protect(l: Long) = (if (this@V2RayVpnService.protect(l.toInt())) 0 else 1).toLong()

        override fun onEmitStatus(l: Long, s: String?): Long {
            Logger.d(s)
            return 0
        }

        override fun setup(s: String): Long {
            Logger.d(s)
            return try {
                this@V2RayVpnService.setup(s)
                0
            } catch (e: Exception) {
                e.printStackTrace()
                -1
            }
        }
    }
}

