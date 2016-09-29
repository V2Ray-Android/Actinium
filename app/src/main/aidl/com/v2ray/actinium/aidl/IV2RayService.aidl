package com.v2ray.actinium.aidl;

import com.v2ray.actinium.aidl.IV2RayServiceCallback;

interface IV2RayService {
    boolean isRunning();
    void stopV2Ray();

    void registerCallback(IV2RayServiceCallback cb);
    void unregisterCallback(IV2RayServiceCallback cb);

    void onPrefForegroundServiceChanged(boolean isEnabled);
}
