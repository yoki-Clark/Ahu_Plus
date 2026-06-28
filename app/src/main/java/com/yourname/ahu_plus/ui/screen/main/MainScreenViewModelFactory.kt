package com.yourname.ahu_plus.ui.screen.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yourname.ahu_plus.AhuPlusApplication
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.repository.AdwmhCardRepository
import com.yourname.ahu_plus.data.repository.CardRepository
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.ExamRepository
import com.yourname.ahu_plus.data.repository.FinanceRepository
import com.yourname.ahu_plus.data.repository.GradeRepository
import com.yourname.ahu_plus.data.repository.JwcNoticeRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.KqAttendanceRepository
import com.yourname.ahu_plus.data.repository.MarketRepository
import com.yourname.ahu_plus.data.repository.StudentInfoRepository
import com.yourname.ahu_plus.data.repository.YcardRepository
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.ui.screen.chaoxing.ChaoxingViewModel
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeListViewModel
import com.yourname.ahu_plus.ui.screen.dashboard.JwcNoticeViewModel
import com.yourname.ahu_plus.ui.screen.emptyclassroom.EmptyClassroomViewModel
import com.yourname.ahu_plus.ui.screen.exam.ExamViewModel
import com.yourname.ahu_plus.ui.screen.grade.GradeViewModel
import com.yourname.ahu_plus.ui.screen.home.HomeViewModel
import com.yourname.ahu_plus.ui.screen.market.MarketViewModel
import com.yourname.ahu_plus.ui.screen.profile.AttendanceViewModel
import com.yourname.ahu_plus.ui.screen.profile.FinanceViewModel
import com.yourname.ahu_plus.ui.screen.profile.StudentInfoViewModel
import com.yourname.ahu_plus.ui.screen.schedule.ScheduleViewModel
import com.yourname.ahu_plus.ui.screen.trainingplan.TrainingPlanViewModel
import com.yourname.ahu_plus.ui.screen.weather.WeatherViewModel
import com.yourname.ahu_plus.ui.screen.welearn.WeLearnViewModel

/**
 * MainScreen 11 个 ViewModel 的工厂。
 *
 * 把 Application + 各个 Repository 集中注入,解决之前 `remember { XxxViewModel(...) }`
 * 写法在 Activity 销毁 / 进程死亡重建时丢失所有 VM 状态的问题。
 *
 * 用法:
 *   val factory = remember(app) { MainScreenViewModelFactory(app, ...) }
 *   val scheduleViewModel: ScheduleViewModel = viewModel(factory = factory)
 */
class MainScreenViewModelFactory(
    private val application: Application,
    private val sessionManager: SessionManager,
    private val cardRepository: CardRepository,
    private val casAuthRepository: CasAuthRepository,
    private val jwAuthRepository: JwAuthRepository,
    private val courseRepository: CourseRepository,
    private val ycardRepository: YcardRepository,
    private val marketRepository: MarketRepository,
    private val jwcNoticeRepository: JwcNoticeRepository,
    private val studentInfoRepository: StudentInfoRepository,
    private val courseNoteRepository: CourseNoteRepository,
    private val gradeRepository: GradeRepository,
    private val examRepository: ExamRepository,
    private val financeRepository: FinanceRepository,
    private val attendanceRepository: KqAttendanceRepository,
    private val adwmhCardRepository: AdwmhCardRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = application as AhuPlusApplication
        return when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(
                cardRepository, casAuthRepository, ycardRepository, sessionManager,
                studentInfoRepository, adwmhCardRepository,
            ) as T
            ScheduleViewModel::class.java -> ScheduleViewModel(
                application = app,
                jwAuthRepository = jwAuthRepository,
                courseRepository = courseRepository,
                noteRepository = courseNoteRepository,
                sessionManager = sessionManager,
                assessmentRepository = app.assessmentRepository,
                recordRepository = app.recordRepository,
                homeworkRepository = app.homeworkRepository,
                userTaskRepository = app.userTaskRepository,
                examRepository = examRepository,
                kqAttendanceRepository = attendanceRepository,
            ) as T
            MarketViewModel::class.java -> MarketViewModel(marketRepository, app.aiCommentRepository) as T
            JwcNoticeViewModel::class.java -> JwcNoticeViewModel(jwcNoticeRepository) as T
            JwcNoticeListViewModel::class.java -> JwcNoticeListViewModel(jwcNoticeRepository) as T
            StudentInfoViewModel::class.java -> StudentInfoViewModel(studentInfoRepository, sessionManager) as T
            GradeViewModel::class.java -> GradeViewModel(jwAuthRepository, gradeRepository, sessionManager) as T
            ExamViewModel::class.java -> ExamViewModel(jwAuthRepository, examRepository, sessionManager) as T
            FinanceViewModel::class.java -> FinanceViewModel(financeRepository, sessionManager) as T
            AttendanceViewModel::class.java -> AttendanceViewModel(attendanceRepository, sessionManager) as T
            TrainingPlanViewModel::class.java -> TrainingPlanViewModel(
                jwAuthRepository = jwAuthRepository,
                trainingPlanRepository = app.trainingPlanRepository,
                completionRepository = app.programCompletionRepository,
                sessionManager = sessionManager,
            ) as T
            EmptyClassroomViewModel::class.java -> EmptyClassroomViewModel(
                jwAuthRepository = jwAuthRepository,
                emptyClassroomRepository = app.emptyClassroomRepository,
                sessionManager = sessionManager,
            ) as T
            ChaoxingViewModel::class.java -> ChaoxingViewModel(
                app.chaoxingRepository, app.chaoxingStudyRepository, app.chaoxingTikuRepository,
                sessionManager,
            ) as T
            WeLearnViewModel::class.java -> WeLearnViewModel(app) as T
            WeatherViewModel::class.java -> WeatherViewModel(app.weatherManager) as T
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}