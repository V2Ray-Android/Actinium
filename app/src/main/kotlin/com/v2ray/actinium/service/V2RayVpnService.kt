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
import com.v2ray.actinium.BuildConfig
import com.v2ray.actinium.R
import com.v2ray.actinium.aidl.IV2RayService
import com.v2ray.actinium.aidl.IV2RayServiceCallback
import com.v2ray.actinium.extension.broadcastAll
import com.v2ray.actinium.ui.BypassListActivity
import com.v2ray.actinium.ui.MainActivity
import com.v2ray.actinium.ui.SettingsActivity
import com.v2ray.actinium.util.ConfigUtil
import com.v2ray.actinium.util.currConfigFile
import go.libv2ray.Libv2ray
import go.libv2ray.V2RayCallbacks
import go.libv2ray.V2RayVPNServiceSupportsSet
import org.jetbrains.anko.defaultSharedPreferences
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class V2RayVpnService : VpnService() {
    companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
        const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
        const val ACTION_STOP_V2RAY = "com.v2ray.actinium.action.STOP_V2RAY"

        fun startV2Ray(context: Context) {

            val intent = Intent(context.applicationContext, V2RayVpnService::class.java)

            val configFile = context.currConfigFile
            intent.putExtra("configPath", configFile.absolutePath)

            val autoRestart = context.defaultSharedPreferences
                    .getBoolean(SettingsActivity.PREF_AUTO_RESTART, false)
                    && ConfigUtil.isKcpConfig(configFile.readText())
            intent.putExtra("autoRestart", autoRestart)

            val foregroundService = context.defaultSharedPreferences
                    .getBoolean(SettingsActivity.PREF_FOREGROUND_SERVICE, false)
            intent.putExtra("foregroundService", foregroundService)

            if (context.defaultSharedPreferences.getBoolean(SettingsActivity.PREF_PER_APP_PROXY, false)) {
                val bypassList = context.defaultSharedPreferences
                        .getStringSet(BypassListActivity.PREF_BYPASS_LIST_SET, HashSet())
                intent.putExtra("bypassList", bypassList.toTypedArray())
            }

            context.startService(intent)
        }
    }

    private val v2rayPoint = Libv2ray.newV2RayPoint()
    private val v2rayCallback = V2RayCallback()
    private var connectivitySubscription: Subscription? = null
    private var bypassList: Array<String>? = null

    private val stopV2RayReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            stopV2Ray()
        }
    }

    var serviceCallbacks = RemoteCallbackList<IV2RayServiceCallback>()

    val binder = object : IV2RayService.Stub() {
        override fun isRunning(): Boolean {
            val isRunning = v2rayPoint.isRunning
                    && VpnService.prepare(this@V2RayVpnService) == null
            return isRunning
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
            if (isEnabled)
                showNotification()
            else
                cancelNotification()
        }

        override fun onTransact(code: Int, data: Parcel?, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.LAST_CALL_TRANSACTION) {
                onRevoke()
                return true
            }

            var packageName: String? = null
            val packages = packageManager.getPackagesForUid(getCallingUid())
            if (packages != null && packages.size > 0) {
                packageName = packages[0]
            }
            if (packageName != BuildConfig.APPLICATION_ID) {
                return false
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

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onRevoke() {
        stopV2Ray()
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

        val conf = File(v2rayPoint.configureFile).readText()
        val dnsServers = ConfigUtil.readDnsServersFromConfig(conf, "8.8.8.8", "8.8.4.4")
        for (dns in dnsServers)
            builder.addDnsServer(dns)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bypassList?.let {
                for (app in it)
                    try {
                        builder.addDisallowedApplication(app)
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
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val configPath = intent.getStringExtra("configPath") ?: currConfigFile.absolutePath
        val autoRestart = intent.getBooleanExtra("autoRestart", false)
        val foregroundService = intent.getBooleanExtra("foregroundService", false)
        bypassList = intent.getStringArrayExtra("bypassList")

        startV2ray(configPath, autoRestart, foregroundService)

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

    private fun startV2ray(configPath: String, autoRestart: Boolean, foregroundService: Boolean) {
        if (!v2rayPoint.isRunning) {

            registerReceiver(stopV2RayReceiver, IntentFilter(ACTION_STOP_V2RAY))

            if (autoRestart)
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
            v2rayPoint.vpnSupportSet = v2rayCallback
            v2rayPoint.configureFile = configPath
            v2rayPoint.runLoop()
        }

        if (foregroundService)
            showNotification()
    }

    private fun stopV2Ray() {
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

    private fun showNotification() {
        val startMainIntent = Intent(applicationContext, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(applicationContext,
                NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val stopV2RayIntent = Intent(ACTION_STOP_V2RAY)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(applicationContext,
                NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val configName = File(v2rayPoint.configureFile).name

        val notification = NotificationCompat.Builder(applicationContext)
                .setSmallIcon(R.drawable.ic_action_logo)
                .setContentTitle(getString(R.string.notification_content_title))
                .setContentText(getString(R.string.notification_content_text, configName))
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
            try {
                this@V2RayVpnService.setup(s)
                return 0
            } catch (e: Exception) {
                e.printStackTrace()
                return -1
            }
        }
    }
}

