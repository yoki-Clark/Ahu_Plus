package com.yourname.ahu_plus.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 用系统浏览器/已注册的应用打开 URL。
 *
 * 找不到可处理的 Activity 时返回 false,由调用方决定是否提示用户。
 */
object BrowserOpener {
    fun open(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
