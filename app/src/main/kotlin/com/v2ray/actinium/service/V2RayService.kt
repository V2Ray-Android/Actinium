package com.v2ray.actinium.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.VpnService
import android.os.IBinder
import android.os.StrictMode
import android.support.v7.app.NotificationCompat
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.github.pwittchen.reactivenetwork.library.Connectivity
import com.github.pwittchen.reactivenetwork.library.ReactiveNetwork
import com.orhanobut.logger.Logger
import com.v2ray.actinium.R
import com.v2ray.actinium.event.*
import com.v2ray.actinium.ui.MainActivity
import com.v2ray.actinium.util.currConfigFile
import go.libv2ray.Libv2ray
import go.libv2ray.V2RayCallbacks
import go.libv2ray.V2RayVPNServiceSupportsSet
import org.jetbrains.anko.notificationManager
import org.jetbrains.anko.startService
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import java.util.concurrent.TimeUnit

class V2RayService : Service() {
    companion object {
        const val NOTIFICATION_ID = 0
        const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
        const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 0
        const val ACTION_STOP_V2RAY = "com.v2ray.actinium.action.STOP_V2RAY"

        var isServiceRunning = false
            private set

        fun startV2Ray(context: Context) {
            context.startService<V2RayService>()
        }

        fun stopV2Ray() {
            Bus.send(StopV2RayEvent)
        }

        fun checkStatusEvent(callback: (Boolean) -> Unit) {
            if (!isServiceRunning) {
                callback(false)
                return
            }
            Bus.send(CheckV2RayStatusEvent(callback))
        }
    }

    private val v2rayPoint = Libv2ray.newV2RayPoint()
    private var vpnService: V2RayVpnService? = null
    private val v2rayCallback = V2RayCallback()
    private var connectivitySubscription: Subscription? = null
    private val stopV2RayReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            stopV2Ray()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        isServiceRunning = true

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        v2rayPoint.packageName = packageName

        Bus.observe<VpnServiceSendSelfEvent>()
                .subscribe {
                    vpnService = it.vpnService
                    vpnCheckIsReady()
                }
                .registerInBus(this)

        Bus.observe<StopV2RayEvent>()
                .subscribe {
                    stopV2Ray()
                }
                .registerInBus(this)

        Bus.observe<VpnServiceStatusEvent>()
                .filter { !it.isRunning }
                .subscribe { stopV2Ray() }
                .registerInBus(this)

        Bus.observe<CheckV2RayStatusEvent>()
                .subscribe {
                    val isRunning = vpnService != null
                            && v2rayPoint.isRunning
                            && VpnService.prepare(this) == null
                    it.callback(isRunning)
                }

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

    override fun onDestroy() {
        super.onDestroy()
        Bus.unregister(this)
        unregisterReceiver(stopV2RayReceiver)
        connectivitySubscription?.let {
            it.unsubscribe()
            connectivitySubscription = null
        }
        isServiceRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startV2ray()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun vpnPrepare(): Int {
        startService<V2RayVpnService>()
        return 1
    }

    private fun vpnCheckIsReady() {
        val prepare = VpnService.prepare(this)

        if (prepare != null) {
            Bus.send(VpnPrepareEvent(prepare) {
                if (it)
                    vpnCheckIsReady()
                else
                    v2rayPoint.stopLoop()
            })
            return
        }

        if (this.vpnService != null) {
            v2rayPoint.vpnSupportReady()
            Bus.send(V2RayStatusEvent(true))
            showNotification()
        }
    }

    private fun startV2ray() {
        Logger.d(v2rayPoint)
        if (!v2rayPoint.isRunning) {
            v2rayPoint.callbacks = v2rayCallback
            v2rayPoint.vpnSupportSet = v2rayCallback
            v2rayPoint.configureFile = currConfigFile.absolutePath
            v2rayPoint.runLoop()
        }
    }

    private fun stopV2Ray() {
        if (v2rayPoint.isRunning) {
            v2rayPoint.stopLoop()
        }
        vpnService = null
        Bus.send(V2RayStatusEvent(false))
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

        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
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
                vpnService!!.setup(s)
                return 0
            } catch (e: Exception) {
                e.printStackTrace()
                return -1
            }
        }
    }
}
