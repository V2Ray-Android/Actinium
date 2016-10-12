package com.v2ray.actinium.extension

import android.preference.Preference

fun Preference.onClick(listener: () -> Unit) {
    setOnPreferenceClickListener {
        listener()
        true
    }
}