package com.v2ray.actinium

import android.app.Application
import android.content.Context
import com.orhanobut.logger.LogLevel
import com.orhanobut.logger.Logger
import com.squareup.leakcanary.LeakCanary
import com.v2ray.actinium.extension.VPN_NETWORK_STATISTICS
import com.v2ray.actinium.ui.SettingsActivity
import com.v2ray.actinium.util.ConfigManager
import me.dozen.dpreference.DPreference
import org.jetbrains.anko.defaultSharedPreferences

class ActiniumApplication : Application() {
    companion object {
        const val PREF_LAST_VERSION = "pref_last_version"
    }

    var firstRun = false
        private set

    val defaultDPreference by lazy { DPreference(this, packageName + "_preferences") }

    override fun onCreate() {
        super.onCreate()

        LeakCanary.install(this)

        val lastVersion = defaultSharedPreferences.getInt(PREF_LAST_VERSION, 0)
        val pureVersion = if (lastVersion > 4000000) lastVersion - 4000000
        else if (lastVersion > 3000000) lastVersion - 3000000
        else if (lastVersion > 2000000) lastVersion - 2000000
        else if (lastVersion > 1000000) lastVersion - 1000000
        else lastVersion

        if (pureVersion < 25)
            getSharedPreferences(VPN_NETWORK_STATISTICS, Context.MODE_PRIVATE).edit().clear().apply()

        if (pureVersion < 29) {
            val autoRestart = defaultSharedPreferences.getBoolean(SettingsActivity.PREF_AUTO_RESTART, false)
            defaultSharedPreferences.edit().remove(SettingsActivity.PREF_AUTO_RESTART).apply()
            if (autoRestart) {
                val set = setOf("kcp")
                defaultSharedPreferences.edit().putStringSet(SettingsActivity.PREF_AUTO_RESTART_SET, set).apply()
                defaultDPreference.setPrefStringSet(SettingsActivity.PREF_AUTO_RESTART_SET, set)}
        }

        firstRun = lastVersion != BuildConfig.VERSION_CODE
        if (firstRun) {
            defaultSharedPreferences.edit().putInt(PREF_LAST_VERSION, BuildConfig.VERSION_CODE).apply()
        }

        Logger.init().logLevel(if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.NONE)
        ConfigManager.inject(this)
    }
}

val Context.v2RayApplication: ActiniumApplication
    get() = applicationContext as ActiniumApplication

val Context.defaultDPreference: DPreference
    get() = v2RayApplication.defaultDPreference