package com.yourname.ahu_plus.data.repository

/**
 * ycard (一卡通) 同步认证过期信号。
 *
 * 由 [YcardRepository] 在 HTTP 401 / 403 时抛出,
 * 上层 (例如 [com.yourname.ahu_plus.ui.screen.home.HomeViewModel]) 用 `is YcardAuthExpiredException`
 * 判断是否触发后台静默重登录 + 重试。
 *
 * 取代旧的 `Result.exceptionOrNull()?.message?.contains("HTTP 401")` 字符串匹配模式。
 */
class YcardAuthExpiredException(message: String) : Exception(message)