package com.v2ray.actinium.ui

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.CheckBoxPreference
import android.preference.MultiSelectListPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.support.v7.app.AppCompatActivity
import com.v2ray.actinium.R
import com.v2ray.actinium.aidl.IV2RayService
import com.v2ray.actinium.defaultDPreference
import com.v2ray.actinium.extension.onClick
import com.v2ray.actinium.service.V2RayVpnService
import de.psdev.licensesdialog.LicensesDialogFragment
import libv2ray.Libv2ray
import org.jetbrains.anko.act
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.startActivity

class SettingsActivity : BaseActivity() {
    companion object {
        const val PREF_START_ON_BOOT = "pref_start_on_boot"
        const val PREF_PER_APP_PROXY = "pref_per_app_proxy"
        const val PREF_LICENSES = "pref_licenses"
        const val PREF_FEEDBACK = "pref_feedback"
        const val PREF_AUTO_RESTART = "pref_auto_restart"
        const val PREF_AUTO_RESTART_SET = "pref_auto_restart_set"
        const val PREF_FOREGROUND_SERVICE = "pref_foreground_service"
        const val PREF_CORE_VERSION = "pref_core_version"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
        val perAppProxy by lazy { findPreference(PREF_PER_APP_PROXY) as CheckBoxPreference }
        val autoRestart by lazy { findPreference(PREF_AUTO_RESTART_SET) as MultiSelectListPreference }
        val licenses: Preference by lazy { findPreference(PREF_LICENSES) }
        val feedback: Preference by lazy { findPreference(PREF_FEEDBACK) }
        val coreVersion: Preference by lazy { findPreference(PREF_CORE_VERSION) }

        var bgService: IV2RayService? = null

        val conn = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
            }

            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val service1 = IV2RayService.Stub.asInterface(service)

                bgService = service1

                val isV2RayRunning = service1.isRunning

                autoRestart.isEnabled = !isV2RayRunning

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (isV2RayRunning) {
                        perAppProxy.isEnabled = false
                    } else {
                        perAppProxy.setOnPreferenceClickListener {
                            startActivity<PerAppProxyActivity>()
                            perAppProxy.isChecked = true
                            false
                        }
                    }
                } else {
                    perAppProxy.isEnabled = false
                }
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_settings)

            licenses.onClick {
                val fragment = LicensesDialogFragment.Builder(act)
                        .setNotices(R.raw.licenses)
                        .setIncludeOwnLicense(false)
                        .build()
                fragment.show((act as AppCompatActivity).supportFragmentManager, null)
            }

            coreVersion.summary = Libv2ray.checkVersionX()
        }

        override fun onStart() {
            super.onStart()

            val intent = Intent(act.applicationContext, V2RayVpnService::class.java)
            act.bindService(intent, conn, BIND_AUTO_CREATE)

            perAppProxy.isChecked = defaultSharedPreferences.getBoolean(PREF_PER_APP_PROXY, false)

            autoRestart.summary = defaultSharedPreferences.getStringSet(PREF_AUTO_RESTART_SET, emptySet()).joinToString { it }

            defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onStop() {
            super.onStop()

            act.unbindService(conn)

            defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            when (key) {
                PREF_FOREGROUND_SERVICE -> {
                    act.defaultDPreference.setPrefBoolean(key, sharedPreferences.getBoolean(key, false))
                    bgService?.onPrefForegroundServiceChanged(sharedPreferences.getBoolean(key, false))
                }

                PREF_AUTO_RESTART ->
                    act.defaultDPreference.setPrefBoolean(key, sharedPreferences.getBoolean(key, false))

                PREF_PER_APP_PROXY ->
                    act.defaultDPreference.setPrefBoolean(key, sharedPreferences.getBoolean(key, false))

                PREF_AUTO_RESTART_SET -> {
                    val set = sharedPreferences.getStringSet(key, null)
                    act.defaultDPreference.setPrefStringSet(key, set)
                    autoRestart.summary = set.joinToString { it }
                }
            }
        }
    }
}