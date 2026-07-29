package com.ahu_plus.data.local

/**
 * 头像显示来源。
 *
 * - [DEFAULT]: 默认 Person 图标
 * - [REAL]: 校园真实相片 (ycard /berserker-base/user 的 data.avatar)
 * - [CUSTOM]: 用户从相册选择并圆形裁剪后存本地的自定头像
 *
 * 持久化到 SessionManager (avatar_mode key, 退登清理)。真实相片缓存文件与
 * 自定头像文件本身的清理由 AvatarStore 在退登时统一处理,切换模式不清缓存。
 */
enum class AvatarMode {
    DEFAULT,
    REAL,
    CUSTOM;

    companion object {
        /** 从 DataStore 字符串还原;非法或缺失返回 [DEFAULT]。 */
        fun fromStored(value: String?): AvatarMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
