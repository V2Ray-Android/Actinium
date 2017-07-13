package com.v2ray.actinium.extension

const val threshold = 1000
const val divisor = 1024F

fun Long.toSpeedString() = toTrafficString() + "/s"

fun Long.toTrafficString(): String {
    if (this < threshold)
        return "$this Bytes"

    val kib = this / divisor
    if (kib < threshold)
        return "${kib.toShortString()} KiB"

    val mib = kib / divisor
    if (mib < threshold)
        return "${mib.toShortString()} MiB"

    val gib = mib / divisor
    if (gib < threshold)
        return "${gib.toShortString()} GiB"

    val tib = gib / divisor
    if (tib < threshold)
        return "${tib.toShortString()} TiB"

    val pib = tib / divisor
    if (pib < threshold)
        return "${pib.toShortString()} PiB"

    return "∞"
}

private fun Float.toShortString(): String {
    val s = toString()
    if (s.length <= 4)
        return s
    return s.substring(0, 4).removeSuffix(".")
}