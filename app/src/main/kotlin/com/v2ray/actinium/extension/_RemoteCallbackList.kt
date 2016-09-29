package com.v2ray.actinium.extension

import android.os.IInterface
import android.os.RemoteCallbackList

inline fun <reified T : IInterface> RemoteCallbackList<T>.broadcastAll(action: (T) -> Unit) {
    val size = beginBroadcast()
    for (i in 0..size - 1)
        action(getBroadcastItem(i))
    finishBroadcast()
}