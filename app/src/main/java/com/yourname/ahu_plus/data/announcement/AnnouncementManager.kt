package com.yourname.ahu_plus.data.announcement

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.Announcement
import com.yourname.ahu_plus.data.repository.AnnouncementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 开发者公告管理器。
 *
 * 状态流转(订阅 [uiState]):
 *   null(无待显示) ──checkAnnouncements()──► 队首公告 ──dismiss()──► 下一条 / null
 *
 * 多条活跃公告按 priority 排队,一条条弹;[dismiss] 推进队列。
 * 设计与 [com.yourname.ahu_plus.data.update.UpdateManager] 同构,
 * 但弹窗为纯展示(不下载),所以状态简化为"当前公告 or null"。
 */
class AnnouncementManager(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val repository: AnnouncementRepository
) {
    /** 当前应展示的公告;null 表示无待办或已全部消费。 */
    private val _uiState = MutableStateFlow<Announcement?>(null)
    val uiState: StateFlow<Announcement?> = _uiState.asStateFlow()

    /** 等待弹出的剩余队列(不含当前正在显示的队首)。 */
    private var queue: List<Announcement> = emptyList()

    companion object {
        private const val TAG = "AnnouncementMgr"
    }

    /**
     * 启动检查:best-effort 拉取远程公告(失败则用本地缓存),
     * 计算活跃列表并显示队首。已在显示中则不重复触发。
     */
    suspend fun checkAnnouncements() {
        if (_uiState.value != null) return
        // 先拉远程,失败不致命(回退到本地缓存)
        repository.fetchRemote()
            .onFailure { Log.w(TAG, "fetchRemote failed, fall back to cache: ${it.message}") }

        val active = repository.getActiveAnnouncements()
        if (active.isEmpty()) {
            Log.d(TAG, "no active announcements")
            return
        }
        queue = active.drop(1)
        _uiState.value = active.first()
        Log.d(TAG, "showing announcement '${active.first().id}', ${queue.size} queued")
    }

    /**
     * 关闭当前公告并弹出下一条。
     *
     * @param dontShowAgain 用户勾选了"不再提示"(仅 dismissible 公告有效) →
     *                      持久化忽略 id,之后启动不再显示。
     */
    suspend fun dismiss(dontShowAgain: Boolean) {
        val current = _uiState.value ?: return
        if (dontShowAgain && current.dismissible) {
            sessionManager.addDismissedAnnouncementId(current.id)
        }
        _uiState.value = queue.firstOrNull()
        queue = queue.drop(1)
    }

    /** 打开公告的动作链接(外部浏览器)。不改变弹窗状态,由 UI 决定是否同时 dismiss。 */
    fun openAction(url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "openAction failed for $url", e)
        }
    }
}
