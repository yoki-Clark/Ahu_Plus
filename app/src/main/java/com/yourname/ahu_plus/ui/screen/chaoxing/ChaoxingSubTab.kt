package com.yourname.ahu_plus.ui.screen.chaoxing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 学习通底部 Tab 内部的二级导航 tab(2026-06-20 v3)。
 *
 * 3 个 tab：课程 | 消息 | 设置
 *   - 签到/题库设置已合并到"设置"页
 *   - 消息为 chaoxing 内部通知（待实现 API）
 */
enum class ChaoxingSubTab(
    val label: String,
    val icon: ImageVector,
) {
    COURSES("课程", Icons.AutoMirrored.Filled.MenuBook),
    MESSAGES("消息", Icons.Filled.MarkEmailUnread),
    SETTINGS("设置", Icons.Filled.Settings),
}
