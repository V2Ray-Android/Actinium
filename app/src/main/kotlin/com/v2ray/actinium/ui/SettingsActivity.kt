package com.v2ray.actinium.ui

import android.os.Bundle
import android.preference.PreferenceFragment
import com.v2ray.actinium.R

class SettingsActivity : BaseActivity() {
    companion object {
        const val PREF_START_ON_BOOT = "pref_start_on_boot"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_settings)
        }
    }
}