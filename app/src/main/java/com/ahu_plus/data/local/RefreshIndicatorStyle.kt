package com.ahu_plus.data.local

/**
 * 下拉刷新指示器的动画样式。
 *
 * 全 App 13 处下拉刷新统一走 [com.ahu_plus.ui.components.AhuPullToRefreshBox],
 * 由用户在「设置 → 外观」选择,经 SessionManager 持久化(退登保留、clearAll 清理),
 * 通过 [com.ahu_plus.ui.components.LocalRefreshIndicatorStyle] 在根部注入,各页面零改动。
 *
 * 与 [HomeDockMode] / [AppAccentColor] 同构:枚举 + [storageValue] + [fromStorageValue]。
 * 默认 [SYSTEM_DEFAULT] 为 Material 原生圆形转圈。
 */
enum class RefreshIndicatorStyle(val storageValue: String) {

    /** Material 原生下拉刷新指示器(圆形容器 + CircularProgressIndicator 转圈,默认) */
    SYSTEM_DEFAULT("system_default"),

    /** 品牌色 sweep 渐变弧 + 旋转(= 历史自定义实现) */
    GRADIENT_ARC("gradient_arc"),

    /** 三点依次弹跳缩放 */
    BOUNCING_DOTS("bouncing_dots"),

    /** 中心点 + 卫星椭圆公转 */
    ORBIT("orbit"),

    /** 同心圆向外脉冲扩散 */
    PULSE_RINGS("pulse_rings");

    companion object {
        val DEFAULT: RefreshIndicatorStyle = SYSTEM_DEFAULT

        fun fromStorageValue(value: String?): RefreshIndicatorStyle =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
