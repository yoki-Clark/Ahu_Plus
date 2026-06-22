package com.yourname.ahu_plus.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

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

    fun openInWeChat(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val weChatWebViewUrl = "weixin://dl/businessWebview/link/?appid=wxb0cc6c5fccbaf1d0&url=$encodedUrl"
        val weChatSchemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(weChatWebViewUrl)).apply {
            setPackage("com.tencent.mm")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(weChatSchemeIntent) }.isSuccess) return true

        val publicSchemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(weChatWebViewUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(publicSchemeIntent) }.isSuccess) return true

        return open(context, url)
    }

    fun shareTextToWeChat(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.tencent.mm")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * 用 QQ 加好友 (mqqapi:// scheme,Android 11+ 需要 <queries> 声明 com.tencent.mobileqq)。
     *
     * @param uin 目标 QQ 号
     * @return true=成功拉起 QQ 加好友界面, false=需 fallback 到网页
     */
    fun openQQ(context: Context, uin: String): Boolean {
        if (uin.isBlank()) return false
        // mqqapi://card/show_pslcard?src_type=internal&version=1&uin=<uin>
        // QQ 客户端会显示"添加好友"资料卡
        val qqUrl = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$uin"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(qqUrl)).apply {
            setPackage("com.tencent.mobileqq")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * 用 QQ 加群 (mqqapi:// scheme + card_type=group + source=qrcode)。
     *
     * @param groupUin QQ 群号
     * @return true=成功拉起 QQ 加群界面, false=需 fallback 到网页
     */
    fun openQQGroup(context: Context, groupUin: String): Boolean {
        if (groupUin.isBlank()) return false
        // mqqapi://card/show_pslcard?...&card_type=group&source=qrcode
        val qqUrl =
            "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$groupUin&card_type=group&source=qrcode"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(qqUrl)).apply {
            setPackage("com.tencent.mobileqq")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * 分享文本到 QQ (ACTION_SEND + setPackage)。
     *
     * @param text 分享的文本内容
     * @return true=成功拉起 QQ 发送界面, false=失败
     */
    fun shareTextToQQ(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.tencent.mobileqq")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
