package com.v2ray.actinium.aidl;

import com.v2ray.actinium.dto.VpnNetworkInfo;

interface IV2RayServiceCallback {
    void onStateChanged(boolean isRunning);
    void onNetworkInfoUpdated(in VpnNetworkInfo info);
}
