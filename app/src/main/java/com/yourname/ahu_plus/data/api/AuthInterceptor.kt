package com.yourname.ahu_plus.data.api

import com.yourname.ahu_plus.data.local.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionManager: SessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val sessionId = sessionManager.getSessionId()

        val request = if (sessionId != null) {
            original.newBuilder()
                .header("Cookie", "JSESSIONID=$sessionId")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/132.0.0.0 Safari/537.36 NetType/WIFI MicroMessenger/7.0.20.1781(0x6700143B) " +
                        "WindowsWechat(0x63090a13) UnifiedPCWindowsWechat(0xf2541a35) XWEB/19977 Flue"
                )
                .header("Referer", "https://adwmh.ahu.edu.cn/www/index.html")
                .header("x-requested-with", "XMLHttpRequest")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
