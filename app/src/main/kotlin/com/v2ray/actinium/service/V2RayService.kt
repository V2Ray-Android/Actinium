package com.v2ray.actinium.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.VpnService
import android.os.IBinder
import android.os.Parcel
import android.os.StrictMode
import android.support.v7.app.NotificationCompat
import com.github.pwittchen.reactivenetwork.library.Connectivity
import com.github.pwittchen.reactivenetwork.library.ReactiveNetwork
import com.hwangjr.rxbus.annotation.Subscribe
import com.orhanobut.logger.Logger
import com.v2ray.actinium.BuildConfig
import com.v2ray.actinium.R
import com.v2ray.actinium.aidl.IV2RayService
import com.v2ray.actinium.aidl.IV2RayServiceCallback
import com.v2ray.actinium.event.StopV2RayEvent
import com.v2ray.actinium.event.V2RayStatusEvent
import com.v2ray.actinium.event.VpnServiceSendSelfEvent
import com.v2ray.actinium.event.VpnServiceStatusEvent
import com.v2ray.actinium.extension.rxBus
import com.v2ray.actinium.ui.MainActivity
import com.v2ray.actinium.util.currConfigFile
import go.libv2ray.Libv2ray
import go.libv2ray.V2RayCallbacks
import go.libv2ray.V2RayVPNServiceSupportsSet
import org.jetbrains.anko.startService
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class V2RayService : Service() {
    companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
        const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
        const val ACTION_STOP_V2RAY = "com.v2ray.actinium.action.STOP_V2RAY"

        var isServiceRunning = false
            private set

        fun startV2Ray(context: Context) {
            context.startService<V2RayService>()
        }
    }

    private val v2rayPoint = Libv2ray.newV2RayPoint()
    private var vpnService: V2RayVpnService? = null
    private val v2rayCallback = V2RayCallback()
    private var connectivitySubscription: Subscription? = null
    private var bypassList: Array<String>? = null

    private val stopV2RayReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            stopV2Ray()
        }
    }

    var serviceCallbacks: MutableSet<IV2RayServiceCallback> = HashSet()

    val binder = object : IV2RayService.Stub() {
        override fun isRunning(): Boolean {
            val isRunning = vpnService != null
                    && v2rayPoint.isRunning
                    && VpnService.prepare(this@V2RayService) == null
            return isRunning
        }

        override fun stopV2Ray() {
            this@V2RayService.stopV2Ray()
        }

        override fun registerCallback(cb: IV2RayServiceCallback?) {
            cb?.let {
                synchronized(serviceCallbacks) { serviceCallbacks.add(it) }
            }
        }

        override fun unregisterCallback(cb: IV2RayServiceCallback?) {
            cb?.let {
                synchronized(serviceCallbacks) { serviceCallbacks.remove(it) }
            }
        }

        override fun onTransact(code: Int, data: Parcel?, reply: Parcel?, flags: Int): Boolean {
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

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        isServiceRunning = true

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        v2rayPoint.packageName = packageName

        rxBus.register(this)

        registerReceiver(stopV2RayReceiver, IntentFilter(ACTION_STOP_V2RAY))

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
    }

    @Subscribe
    fun onVpnServiceSendSelf(event: VpnServiceSendSelfEvent) {
        vpnService = event.vpnService
        vpnCheckIsReady()
    }

    @Subscribe
    fun onStopV2Ray(event: StopV2RayEvent) {
        stopV2Ray()
    }

    @Subscribe
    fun onVpnServiceStatusChanged(event: VpnServiceStatusEvent) {
        if (!event.isRunning)
            stopV2Ray()
    }

    override fun onDestroy() {
        super.onDestroy()
        rxBus.unregister(this)
        unregisterReceiver(stopV2RayReceiver)
        connectivitySubscription?.let {
            it.unsubscribe()
            connectivitySubscription = null
        }
        isServiceRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val configPath = intent?.getStringExtra("configPath") ?: currConfigFile.absolutePath
        bypassList = intent?.getStringArrayExtra("bypassList")
        startV2ray(configPath)

        val newFlags = flags or START_STICKY
        return super.onStartCommand(intent, newFlags, startId)
    }

    private fun vpnPrepare(): Int {
        startService<V2RayVpnService>()
        return 1
    }

    private fun vpnCheckIsReady() {
        val prepare = VpnService.prepare(this)

        if (prepare != null) {
            return
        }

        if (this.vpnService != null) {
            v2rayPoint.vpnSupportReady()
            rxBus.post(V2RayStatusEvent(true))
            serviceCallbacks.forEach { it.onStateChanged(true) }
            showNotification()
        }
    }

    private fun startV2ray(configPath: String) {
        Logger.d(v2rayPoint)
        if (!v2rayPoint.isRunning) {
            v2rayPoint.callbacks = v2rayCallback
            v2rayPoint.vpnSupportSet = v2rayCallback
            v2rayPoint.configureFile = configPath
            v2rayPoint.runLoop()
        }
    }

    private fun stopV2Ray() {
        if (v2rayPoint.isRunning) {
            v2rayPoint.stopLoop()
        }
        vpnService = null
        rxBus.post(V2RayStatusEvent(false))
        serviceCallbacks.forEach { it.onStateChanged(false) }
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

        override fun getVPNFd() = vpnService!!.fd.toLong()

        override fun prepare() = vpnPrepare().toLong()

        override fun protect(l: Long) = (if (vpnService!!.protect(l.toInt())) 0 else 1).toLong()

        override fun onEmitStatus(l: Long, s: String?): Long {
            Logger.d(s)
            return 0
        }

        override fun setup(s: String): Long {
            Logger.d(s)
            try {
                vpnService!!.setup(s, v2rayPoint.configureFile, bypassList)
                return 0
            } catch (e: Exception) {
                e.printStackTrace()
                return -1
            }
        }
    }
}
