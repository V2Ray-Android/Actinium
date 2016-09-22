package com.v2ray.actinium

import android.app.Application
import android.content.Context
import com.orhanobut.logger.LogLevel
import com.orhanobut.logger.Logger
import com.squareup.leakcanary.LeakCanary
import com.v2ray.actinium.util.ConfigManager
import org.jetbrains.anko.defaultSharedPreferences

class ActiniumApplication : Application() {
    companion object {
        const val PREF_LAST_VERSION = "pref_last_version"
    }

    var firstRun = false
        private set

    override fun onCreate() {
        super.onCreate()

        LeakCanary.install(this)

        firstRun = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0) != BuildConfig.VERSION_CODE
        if (firstRun)
            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE).apply()

        Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        ConfigManager.inject(this)
    }
}

val Context.v2RayApplication: ActiniumApplication
    get() = applicationContext as ActiniumApplication