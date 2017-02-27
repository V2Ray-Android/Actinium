package com.v2ray.actinium.extension

import android.content.Context
import android.os.Parcel
import android.util.Base64
import com.v2ray.actinium.dto.VpnNetworkInfo
import me.dozen.dpreference.DPreference

const val VPN_NETWORK_STATISTICS = "vpn_network_statistics"

fun Context.loadVpnNetworkInfo(configName: String,defaultValue:VpnNetworkInfo?): VpnNetworkInfo? {
    try {
        val raw = DPreference(this, VPN_NETWORK_STATISTICS).getPrefString(configName, null)
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        val ret = VpnNetworkInfo(parcel)
        parcel.recycle()
        return ret
    } catch (ignore: Exception) {
    }
    return defaultValue
}

fun Context.saveVpnNetworkInfo(configName: String, info: VpnNetworkInfo)
        = DPreference(this, VPN_NETWORK_STATISTICS).setPrefString(configName, info.serializeString())

