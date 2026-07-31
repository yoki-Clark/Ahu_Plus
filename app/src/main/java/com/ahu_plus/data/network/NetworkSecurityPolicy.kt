package com.ahu_plus.data.network

object CleartextHostPolicy {
    val allowedHosts: Set<String> = setOf(
        "adwmh.ahu.edu.cn",
        "wvpn.ahu.edu.cn",
        "welearn.sflep.com",
        "sso.sflep.com",
        "172.17.106.232",
    )

    fun isAllowed(host: String): Boolean = host.trim().lowercase() in allowedHosts
}
