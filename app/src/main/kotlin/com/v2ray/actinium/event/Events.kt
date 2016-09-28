package com.v2ray.actinium.event

import com.v2ray.actinium.service.V2RayVpnService

object StopV2RayEvent

data class V2RayStatusEvent(val isRunning: Boolean)

data class VpnServiceSendSelfEvent(val vpnService: V2RayVpnService)

data class VpnServiceStatusEvent(val isRunning: Boolean)
