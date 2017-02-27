package com.v2ray.actinium.dto

import android.os.Parcel
import android.os.Parcelable
import android.util.Base64

data class VpnNetworkInfo(val rxByte: Int = 0, val rxPacket: Int = 0, val txByte: Int = 0,
                          val txPacket: Int = 0) : Parcelable {
    companion object {
        @JvmField val CREATOR: Parcelable.Creator<VpnNetworkInfo> = object : Parcelable.Creator<VpnNetworkInfo> {
            override fun createFromParcel(source: Parcel): VpnNetworkInfo = VpnNetworkInfo(source)
            override fun newArray(size: Int): Array<VpnNetworkInfo?> = arrayOfNulls(size)
        }
    }

    constructor(source: Parcel) : this(source.readInt(), source.readInt(), source.readInt(), source.readInt())

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeInt(rxByte)
        dest?.writeInt(rxPacket)
        dest?.writeInt(txByte)
        dest?.writeInt(txPacket)
    }

    operator infix fun minus(other: VpnNetworkInfo)
            = VpnNetworkInfo(
            rxByte - other.rxByte,
            rxPacket - other.rxPacket,
            txByte - other.txByte,
            txPacket - other.txPacket
    )

    operator infix fun plus(other: VpnNetworkInfo)
            = VpnNetworkInfo(
            rxByte + other.rxByte,
            rxPacket + other.rxPacket,
            txByte + other.txByte,
            txPacket + other.txPacket
    )

    fun serializeString(): String {
        val parcel = Parcel.obtain()
        parcel.setDataPosition(0)
        writeToParcel(parcel, 0)
        val ret = Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
        parcel.recycle()
        return ret
    }
}