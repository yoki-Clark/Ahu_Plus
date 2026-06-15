package com.yourname.ahu_plus

import android.app.Application
import com.yourname.ahu_plus.data.local.AppDataStore
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.repository.CardRepository
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.JwcNoticeRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.MarketRepository
import com.yourname.ahu_plus.data.repository.StudentInfoRepository
import com.yourname.ahu_plus.data.repository.YcardRepository

class AhuPlusApplication : Application() {
    lateinit var appDataStore: AppDataStore
        private set
    lateinit var sessionManager: SessionManager
        private set
    lateinit var courseNoteRepository: CourseNoteRepository
        private set
    lateinit var cardRepository: CardRepository
        private set
    lateinit var casAuthRepository: CasAuthRepository
        private set
    lateinit var jwAuthRepository: JwAuthRepository
        private set
    lateinit var courseRepository: CourseRepository
        private set
    lateinit var ycardRepository: YcardRepository
        private set
    lateinit var marketRepository: MarketRepository
        private set
    lateinit var jwcNoticeRepository: JwcNoticeRepository
        private set
    lateinit var studentInfoRepository: StudentInfoRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // ── 基础存储层 ─────────────────────────────
        appDataStore = AppDataStore(this)
        sessionManager = SessionManager(appDataStore)
        courseNoteRepository = CourseNoteRepository(appDataStore)

        // ── 认证层 ────────────────────────────────
        casAuthRepository = CasAuthRepository(sessionManager)
        jwAuthRepository = JwAuthRepository(sessionManager, casAuthRepository)

        // ── 业务 Repository ────────────────────────
        courseRepository = CourseRepository(jwAuthRepository)
        // ycard 复用 CasAuthRepository 的 CASTGC,避免重复完整登录
        ycardRepository = YcardRepository(casAuthRepository)
        cardRepository = CardRepository(
            sessionManager,
            portalJsessionIdProvider = { casAuthRepository.getJsessionid() }
        )
        marketRepository = MarketRepository(sessionManager)
        jwcNoticeRepository = JwcNoticeRepository()
        studentInfoRepository = StudentInfoRepository(sessionManager, casAuthRepository)
    }

    /**
     * 退出登录时统一清理:
     * 1. SessionManager 清掉持久化数据(DataStore + 加密 SharedPreferences)
     * 2. 各 Repository 清掉内存 Cookie 和 JWT
     *
     * 备注:课程备注 [courseNoteRepository] 不被清空 — 用户重新登录后仍能看到自己之前写的备注。
     */
    suspend fun clearAllSessions() {
        casAuthRepository.clearCookies()
        jwAuthRepository.clearCookies()
        ycardRepository.clearCookies()
        sessionManager.clearAll()
    }
}
