package com.yourname.ahu_plus

import android.app.Application
import com.yourname.ahu_plus.data.local.AppDataStore
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.repository.AdwmhCardRepository
import com.yourname.ahu_plus.data.repository.AiCommentRepository
import com.yourname.ahu_plus.data.repository.AssessmentRepository
import com.yourname.ahu_plus.data.repository.ChaoxingNotificationRepository
import com.yourname.ahu_plus.data.repository.ChaoxingRepository
import com.yourname.ahu_plus.data.repository.CloudBackupManager
import com.yourname.ahu_plus.data.repository.CloudStorageRepository
import com.yourname.ahu_plus.data.repository.ChaoxingStudyRepository
import com.yourname.ahu_plus.data.repository.ChaoxingTikuRepository
import com.yourname.ahu_plus.data.repository.KqAttendanceRepository
import com.yourname.ahu_plus.data.update.UpdateManager
import com.yourname.ahu_plus.data.repository.CardRepository
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.EmptyClassroomRepository
import com.yourname.ahu_plus.data.repository.ExamRepository
import com.yourname.ahu_plus.data.repository.FinanceRepository
import com.yourname.ahu_plus.data.repository.GradeRepository
import com.yourname.ahu_plus.data.repository.HomeworkRepository
import com.yourname.ahu_plus.data.repository.ProgramCompletionRepository
import com.yourname.ahu_plus.data.repository.TrainingPlanRepository
import com.yourname.ahu_plus.data.repository.JwcNoticeRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.MarketRepository
import com.yourname.ahu_plus.data.repository.RecordRepository
import com.yourname.ahu_plus.data.repository.StudentInfoRepository
import com.yourname.ahu_plus.data.repository.UserTaskRepository
import com.yourname.ahu_plus.data.repository.YcardRepository
import com.yourname.ahu_plus.util.CxFontDecoder

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
    lateinit var aiCommentRepository: AiCommentRepository
        private set
    lateinit var jwcNoticeRepository: JwcNoticeRepository
        private set
    lateinit var studentInfoRepository: StudentInfoRepository
        private set
    lateinit var gradeRepository: GradeRepository
        private set
    lateinit var examRepository: ExamRepository
        private set
    lateinit var emptyClassroomRepository: EmptyClassroomRepository
        private set
    lateinit var financeRepository: FinanceRepository
        private set
    lateinit var attendanceRepository: KqAttendanceRepository
        private set
    lateinit var trainingPlanRepository: TrainingPlanRepository
        private set
    lateinit var programCompletionRepository: ProgramCompletionRepository
        private set
    lateinit var adwmhCardRepository: AdwmhCardRepository
        private set
    lateinit var assessmentRepository: AssessmentRepository
        private set
    lateinit var recordRepository: RecordRepository
        private set
    lateinit var homeworkRepository: HomeworkRepository
        private set
    lateinit var userTaskRepository: UserTaskRepository
        private set
    lateinit var chaoxingRepository: ChaoxingRepository
        private set
    lateinit var chaoxingTikuRepository: ChaoxingTikuRepository
        private set
    lateinit var chaoxingStudyRepository: ChaoxingStudyRepository
        private set
    lateinit var chaoxingNotificationRepository: ChaoxingNotificationRepository
        private set
    lateinit var cloudStorageRepository: CloudStorageRepository
        private set
    lateinit var cloudBackupManager: CloudBackupManager
        private set
    lateinit var updateManager: UpdateManager
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
            portalJsessionIdProvider = { casAuthRepository.getJsessionid() }
        )
        marketRepository = MarketRepository(sessionManager)
        aiCommentRepository = AiCommentRepository(this, sessionManager)
        jwcNoticeRepository = JwcNoticeRepository()
        studentInfoRepository = StudentInfoRepository(sessionManager, casAuthRepository)
        // 成绩 / 考试 复用 JwAuthRepository 的 CookieJar
        gradeRepository = GradeRepository(jwAuthRepository)
        examRepository = ExamRepository(jwAuthRepository)
        emptyClassroomRepository = EmptyClassroomRepository(jwAuthRepository)
        trainingPlanRepository = TrainingPlanRepository(jwAuthRepository)
        programCompletionRepository = ProgramCompletionRepository(jwAuthRepository)
        // 财务汇总 / 考勤缺勤 复用 studentInfoRepository 的 SSO 会话 (tp_ep_stu)
        financeRepository = FinanceRepository(sessionManager, casAuthRepository)
        attendanceRepository = KqAttendanceRepository(casAuthRepository, sessionManager)
        adwmhCardRepository = AdwmhCardRepository(sessionManager)
        // 课表 2.0 仓储 (2026-06-17)
        assessmentRepository = AssessmentRepository(appDataStore, this)
        recordRepository = RecordRepository(sessionManager)
        homeworkRepository = HomeworkRepository(sessionManager)
        userTaskRepository = UserTaskRepository(sessionManager)
        // 超星学习通 (2026-06-20)
        chaoxingRepository = ChaoxingRepository(sessionManager)
        chaoxingTikuRepository = ChaoxingTikuRepository(sessionManager)
        chaoxingNotificationRepository = ChaoxingNotificationRepository(sessionManager)
        chaoxingStudyRepository = ChaoxingStudyRepository(
            chaoxingRepository, chaoxingTikuRepository, sessionManager,
            notificationRepo = chaoxingNotificationRepository,
        )
        // 腾讯云 COS 云存储 (2026-06-20)
        cloudStorageRepository = CloudStorageRepository(this)
        cloudBackupManager = CloudBackupManager(this, sessionManager, appDataStore, cloudStorageRepository)
        // 将 backupManager 注入 sessionManager，供数据变更时触发防抖备份
        sessionManager.setBackupManager(cloudBackupManager)

        // 初始化超星加密字体解码器(2026-06-20 集成 Phase 1)
        // 启动时一次性加载 assets/font_map_table.json (1.6MB) 到内存 hash map。
        CxFontDecoder.init(this)

        // 自动更新管理器
        updateManager = UpdateManager(this, sessionManager)
    }

    /**
     * 退出登录时统一清理:
     * 1. SessionManager 清掉持久化数据(DataStore)
     * 2. 各 Repository 清掉内存 Cookie 和 JWT
     *
     * 备注:课程备注 [courseNoteRepository] 不被清空 — 用户重新登录后仍能看到自己之前写的备注。
     */
    suspend fun clearAllSessions() {
        casAuthRepository.clearCookies()
        jwAuthRepository.clearCookies()
        ycardRepository.clearCookies()
        adwmhCardRepository.clearCookies()
        sessionManager.clearAll()
    }

    override fun onTerminate() {
        super.onTerminate()
        cloudStorageRepository.shutdown()
    }
}
