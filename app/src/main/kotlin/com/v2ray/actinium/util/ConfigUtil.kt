package com.v2ray.actinium.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.apache.commons.validator.routines.InetAddressValidator
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.regex.Pattern

object ConfigUtil {
    val domainPattern: Pattern by lazy { Pattern.compile("^(([a-zA-Z]{1})|([a-zA-Z]{1}[a-zA-Z]{1})|([a-zA-Z]{1}[0-9]{1})|([0-9]{1}[a-zA-Z]{1})|([a-zA-Z0-9][a-zA-Z0-9-_]{1,61}[a-zA-Z0-9]))\\.([a-zA-Z]{2,6}|[a-zA-Z0-9-]{2,30}\\.[a-zA-Z]{2,3})$") }

    val replacementPairs by lazy {
        mapOf("port" to 10808,
                "inbound" to JSONObject("""{
                    "domainOverride": ["http", "tls"],
                    "protocol": "socks",
                    "listen": "127.0.0.1",
                    "settings": {
                        "auth": "noauth",
                        "udp": true
                    }
                }"""),
                "#lib2ray" to JSONObject("""{
                    "enabled": true,
                    "listener": {
                    "onUp": "#none",
                    "onDown": "#none"
                    },
                    "env": [
                    "V2RaySocksPort=10808"
                    ],
                    "render": [],
                    "escort": [],
                    "vpnservice": {
                    "Target": "${"$"}{datadir}tun2socks",
                    "Args": [
                    "--netif-ipaddr",
                    "26.26.26.2",
                    "--netif-netmask",
                    "255.255.255.0",
                    "--socks-server-addr",
                    "127.0.0.1:${"$"}V2RaySocksPort",
                    "--tunfd",
                    "3",
                    "--tunmtu",
                    "1500",
                    "--sock-path",
                    "/dev/null",
                    "--loglevel",
                    "4",
                    "--enable-udprelay"
                    ],
                    "VPNSetupArg": "m,1500 a,26.26.26.1,24 r,0.0.0.0,0"
                    }
                }"""),
                "log" to JSONObject("""{
                    "loglevel": "warning"
                }"""),
                "inboundDetour" to JSONArray()
        )
    }

    fun validConfig(conf: String): Boolean {
        try {
            val jObj = JSONObject(conf)
            return jObj.has("outbound") and jObj.has("inbound")
        } catch (e: JSONException) {
            return false
        }
    }

    fun isConfigCompatible(conf: String) = JSONObject(conf).has("#lib2ray")

    fun convertConfig(conf: String): String {
        val jObj = JSONObject(conf)

        val replacementPairs = replacementPairs

        /*val vpoint = readVPointFromConfig(jObj)
        if (vpoint != null) {
            val addr = readAddressFromVPoint(vpoint)

            if (domainPattern.matcher(addr).matches()) {
                val preparedDomainName = JSONObject("""{
                    "domainName":[
                    "%domain%:%port%"
                    ],
                    "tcpVersion":"tcp4",
                    "udpVersion":"udp4"
                }"""
                        .replace("%domain%", addr!!).replace("%port%", readPortFromVPoint(vpoint)!!))

                (replacementPairs["#lib2ray"] as JSONObject)
                        .putOpt("preparedDomainName", preparedDomainName)
            }
        }*/

        jObj.putOpt(replacementPairs)

        return jObj.toString()
    }

    fun readDnsServersFromConfig(conf: String, vararg defaultDns: String): Array<out String> {
        val json = JSONObject(conf)

        if (!json.has("dns"))
            return defaultDns
        val dns = json.optJSONObject("dns")

        if (!dns.has("servers"))
            return defaultDns
        val servers = dns.optJSONArray("servers")

        val ret = LinkedHashSet<String>()
        for (i in 0..servers.length() - 1) {
            val e = servers.getString(i)

            if (InetAddressValidator.getInstance().isValid(e))
                ret.add(e)
            else if (e == "localhost")
                NetworkUtil.getDnsServers()?.let { ret.addAll(it) }
        }

        ret.addAll(defaultDns)

        return ret.toTypedArray()
    }

    fun getOutboundFromConfig(json: JSONObject): JSONObject? {
        if (!json.has("outbound"))
            return null
        val outbound = json.optJSONObject("outbound")

        return outbound
    }

    fun getOutboundFromConfig(conf: String) = getOutboundFromConfig(JSONObject(conf))

    fun readVPointFromConfig(conf: String): JSONObject? {
        val outbound = getOutboundFromConfig(conf) ?: return null

        if (!outbound.has("settings"))
            return null
        val settings = outbound.optJSONObject("settings")

        if (!settings.has("vnext"))
            return null
        val vnext = settings.optJSONArray("vnext")

        if (vnext.length() < 1)
            return null
        val vpoint = vnext.optJSONObject(0)

        return vpoint
    }

    fun readVPointFromConfig(conf: JSONObject): JSONObject? {
        val outbound = getOutboundFromConfig(conf) ?: return null

        if (!outbound.has("settings"))
            return null
        val settings = outbound.optJSONObject("settings")

        if (!settings.has("vnext"))
            return null
        val vnext = settings.optJSONArray("vnext")

        if (vnext.length() < 1)
            return null
        val vpoint = vnext.optJSONObject(0)

        return vpoint
    }

    fun readAddressFromConfig(conf: String): String? {
        val vpoint = readVPointFromConfig(conf) ?: return null
        return readAddressFromVPoint(vpoint)
    }

    fun readAddressFromVPoint(vpoint: JSONObject): String? {
        if (!vpoint.has("address"))
            return null
        val address = vpoint.optString("address")

        return address
    }

    fun readPortFromConfig(conf: String): String? {
        val vpoint = readVPointFromConfig(conf) ?: return null
        return readPortFromVPoint(vpoint)
    }

    fun readPortFromVPoint(vpoint: JSONObject): String? {
        if (!vpoint.has("port"))
            return null
        val address = vpoint.optString("port")

        return address
    }

    fun isKcpConfig(conf: String): Boolean {
        val outbound = getOutboundFromConfig(conf) ?: return false

        if (!outbound.has("streamSettings"))
            return false
        val streamSettings = outbound.optJSONObject("streamSettings")

        if (!streamSettings.has("network"))
            return false
        val network = streamSettings.optString("network")

        return network.equals("kcp", ignoreCase = true)
    }

    fun readAddressByName(name: String): String? {
        val conf = ConfigManager.getConfigFileByName(name).readText()
        return readAddressFromConfig(conf)
    }

    fun formatJSON(src: String): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jp = JsonParser()
        val je = jp.parse(src)
        val prettyJsonString = gson.toJson(je)
        return prettyJsonString
    }
}

fun JSONObject.putOpt(pair: Pair<String, Any>) = putOpt(pair.first, pair.second)!!
fun JSONObject.putOpt(pairs: List<Pair<String, Any>>) = pairs.forEach { putOpt(it) }
fun JSONObject.putOpt(pairs: Map<String, Any>) = pairs.forEach { putOpt(it.key to it.value) }