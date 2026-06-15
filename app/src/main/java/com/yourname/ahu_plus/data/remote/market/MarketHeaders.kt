package com.yourname.ahu_plus.data.remote.market

import okhttp3.Request

/**
 * 给 OkHttp [Request.Builder] 一次性打上集市接口需要的全部 Header。
 *
 * 包括：Host / Connection / xweb_xhr / Content-Type / Tenant=7 / Sec-Fetch-* /
 * Referer / Accept-Language / User-Agent / Authorization。
 */
fun Request.Builder.applyMarketHeaders(identity: String): Request.Builder =
    header("Host", "api.zxs-bbs.cn")
        .header("Connection", "keep-alive")
        .header("xweb_xhr", "1")
        .header("Content-Type", "application/json")
        .header("Tenant", "7")
        .header("Accept", "*/*")
        .header("Sec-Fetch-Site", "cross-site")
        .header("Sec-Fetch-Mode", "cors")
        .header("Sec-Fetch-Dest", "empty")
        .header("Referer", MarketApi.REFERER)
        .header("Accept-Language", "zh-CN,zh;q=0.9")
        .header("User-Agent", MarketApi.USER_AGENT)
        .header("Authorization", MarketApi.normalizeIdentity(identity))
