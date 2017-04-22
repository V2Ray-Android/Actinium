package com.v2ray.actinium.util

import android.content.Context
import com.v2ray.actinium.ActiniumApplication
import com.v2ray.actinium.defaultDPreference
import java.io.File

object ConfigManager {
    const val PREF_CURR_CONFIG = "pref_curr_config"

    private lateinit var app: ActiniumApplication

    val configFileDir by lazy { File(app.filesDir, "configs") }

    val configs: Array<out String> get() = configFileDir.list()

    fun inject(app: ActiniumApplication) {
        this.app = app
        if (app.firstRun) {
            if (!configFileDir.exists() || !configFileDir.isDirectory)
                configFileDir.mkdirs()

            // TODO: The official server has been suspended
            AssetsUtil.copyAsset(app.assets, "conf_default.json", File(configFileDir, "default").absolutePath)
        }
    }

    fun getConfigFileByName(name: String) = File(configFileDir, name)

    fun delConfigFileByName(name: String) = getConfigFileByName(name).delete()

    fun delConfigFilesByName(names: Collection<String>) = names.forEach { delConfigFileByName(it) }

}

val Context.currConfigFile: File get() = File(ConfigManager.configFileDir, currConfigName)

val Context.currConfigName: String get() = defaultDPreference.getPrefString(ConfigManager.PREF_CURR_CONFIG, "default")