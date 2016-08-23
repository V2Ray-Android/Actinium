package com.v2ray.actinium.ui

import android.os.Build
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import com.v2ray.actinium.R
import com.v2ray.actinium.service.V2RayService
import org.jetbrains.anko.startActivity

class SettingsActivity : BaseActivity() {
    companion object {
        const val PREF_START_ON_BOOT = "pref_start_on_boot"
        const val PREF_BLACKLIST = "pref_blacklist"
        const val PREF_EDIT_BLACKLIST = "pref_edit_blacklist"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragment() {
        val blacklist by lazy { findPreference(PREF_BLACKLIST) as CheckBoxPreference }
        val editBlacklist: Preference by lazy { findPreference(PREF_EDIT_BLACKLIST) }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_settings)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                V2RayService.checkStatusEvent {
                    if (!it) {
                        editBlacklist.setOnPreferenceClickListener {
                            startActivity<BlacklistActivity>()
                            true
                        }
                    } else {
                        blacklist.isEnabled = false
                        editBlacklist.isEnabled = false
                    }
                }
            } else {
                blacklist.isEnabled = false
                editBlacklist.isEnabled = false
            }
        }
    }
}