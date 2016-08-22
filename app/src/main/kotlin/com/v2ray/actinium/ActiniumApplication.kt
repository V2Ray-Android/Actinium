package com.v2ray.actinium

import android.app.Application
import android.content.Context
import com.orhanobut.logger.LogLevel
import com.orhanobut.logger.Logger
import com.v2ray.actinium.util.ConfigManager

class ActiniumApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        ConfigManager.inject(this)
    }
}

val Context.v2RayApplication: ActiniumApplication
    get() = applicationContext as ActiniumApplication