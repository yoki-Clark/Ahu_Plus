package com.ahu_plus.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 通过 mqqapi 深链打开 QQ 群资料卡,提示用户加群。
 *
 * - 未安装手机 QQ → 返回 false,调用方回退到剪贴板 + Toast
 * - 深链格式来自 QQ 官方 SDK 文档;uin 是纯群号
 */
object QqGroupOpener {
    fun open(context: Context, uin: String): Boolean {
        if (uin.isBlank()) return false
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "mqqapi://card/show_pslcard?src_type=internal&version=1" +
                    "&uin=$uin&card_type=group&source=qrcode"
            )
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
