package com.ahu_plus.data.developer

import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.BuildConfig
import com.ahu_plus.data.debug.DebugClock
import com.ahu_plus.data.home.AppRegistry
import com.google.gson.JsonParser
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.net.ssl.SSLContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class DeveloperTestCategory {
    LOCAL,
    AUTHENTICATION,
    ACADEMIC,
    STUDENT_SERVICES,
    CAMPUS_CARD,
    THIRD_PARTY,
    PUBLIC_SERVICE,
}

enum class DeveloperTestRisk {
    LOCAL_ONLY,
    PUBLIC_READ,
    AUTHENTICATED_READ,
}

enum class DeveloperTestStatus {
    NOT_RUN,
    RUNNING,
    PASSED,
    FAILED,
    TIMED_OUT,
    SKIPPED,
    CANCELLED,
}

data class DeveloperModuleTest(
    val id: String,
    val category: DeveloperTestCategory,
    val title: String,
    val description: String,
    val risk: DeveloperTestRisk,
    val status: DeveloperTestStatus = DeveloperTestStatus.NOT_RUN,
    val result: String? = null,
    val durationMillis: Long? = null,
    val lastRunAtMillis: Long? = null,
)

internal data class DeveloperTestExecution(
    val status: DeveloperTestStatus,
    val result: String,
    val durationMillis: Long,
)

internal class DeveloperTestUnavailableException(message: String) : Exception(message)

internal suspend fun executeDeveloperModuleTest(
    timeoutMillis: Long,
    action: suspend () -> String,
): DeveloperTestExecution {
    require(timeoutMillis > 0L) { "timeoutMillis must be positive" }
    val startedNanos = System.nanoTime()
    val outcome = try {
        DeveloperTestStatus.PASSED to withTimeout(timeoutMillis) { action() }
    } catch (_: TimeoutCancellationException) {
        DeveloperTestStatus.TIMED_OUT to "检查超时"
    } catch (error: DeveloperTestUnavailableException) {
        DeveloperTestStatus.SKIPPED to (error.message ?: "当前条件不可用")
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val type = error.javaClass.simpleName.takeIf(String::isNotBlank) ?: "UnknownError"
        DeveloperTestStatus.FAILED to "检查失败（$type）"
    }
    val durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
    return DeveloperTestExecution(outcome.first, outcome.second, durationMillis)
}

/** Runs side-effect-free server checks through the repositories owned by [AhuPlusApplication]. */
class DeveloperModuleTestRepository(
    private val application: AhuPlusApplication,
) {
    private data class Definition(
        val test: DeveloperModuleTest,
        val timeoutMillis: Long,
        val action: suspend () -> String,
    )

    private val definitions = buildDefinitions()
    private val definitionsById = definitions.associateBy { it.test.id }
    private val runMutex = Mutex()
    private val _tests = MutableStateFlow(definitions.map(Definition::test))

    val tests: StateFlow<List<DeveloperModuleTest>> = _tests.asStateFlow()

    fun getTests(): List<DeveloperModuleTest> = tests.value

    suspend fun run(id: String): DeveloperModuleTest = runMutex.withLock {
        val definition = requireNotNull(definitionsById[id]) { "Unknown developer test id: $id" }
        val running = definition.test.copy(
            status = DeveloperTestStatus.RUNNING,
            result = null,
            durationMillis = null,
            lastRunAtMillis = null,
        )
        replace(running)

        val execution = try {
            executeDeveloperModuleTest(definition.timeoutMillis, definition.action)
        } catch (cancelled: CancellationException) {
            replace(
                running.copy(
                    status = DeveloperTestStatus.CANCELLED,
                    result = "检查已取消",
                    durationMillis = null,
                    lastRunAtMillis = System.currentTimeMillis(),
                )
            )
            throw cancelled
        }

        running.copy(
            status = execution.status,
            result = execution.result,
            durationMillis = execution.durationMillis,
            lastRunAtMillis = System.currentTimeMillis(),
        ).also(::replace)
    }

    private fun replace(test: DeveloperModuleTest) {
        _tests.update { current -> current.map { if (it.id == test.id) test else it } }
    }

    private fun buildDefinitions(): List<Definition> = listOf(
        definition(
            id = "local.storage",
            category = DeveloperTestCategory.LOCAL,
            title = "应用存储",
            description = "检查应用文件、缓存和免备份目录是否可读写。",
            risk = DeveloperTestRisk.LOCAL_ONLY,
            timeoutMillis = LOCAL_TIMEOUT_MILLIS,
        ) {
            val directories = listOf(
                application.filesDir,
                application.cacheDir,
                application.noBackupFilesDir,
            )
            check(directories.all { it.isUsableDirectory() })
            "${directories.size} 个应用存储目录可读写"
        },
        definition(
            id = "local.build",
            category = DeveloperTestCategory.LOCAL,
            title = "构建配置",
            description = "检查版本、应用 ID 和构建类型等基础配置。",
            risk = DeveloperTestRisk.LOCAL_ONLY,
            timeoutMillis = LOCAL_TIMEOUT_MILLIS,
        ) {
            check(BuildConfig.VERSION_CODE > 0)
            check(BuildConfig.VERSION_NAME.isNotBlank())
            check(BuildConfig.BUILD_TYPE.isNotBlank())
            check(BuildConfig.APPLICATION_ID == application.packageName)
            "构建配置有效"
        },
        definition(
            id = "local.datastore",
            category = DeveloperTestCategory.LOCAL,
            title = "DataStore 健康",
            description = "扫描持久化数据的类型与 JSON 结构，仅统计条目，不读取或展示敏感值。",
            risk = DeveloperTestRisk.LOCAL_ONLY,
            timeoutMillis = LOCAL_TIMEOUT_MILLIS,
        ) {
            val report = DeveloperCacheRepository(application.appDataStore).inspect()
            check(report.invalidJsonCount == 0) {
                "发现 ${report.invalidJsonCount} 个异常 JSON 项"
            }
            "${report.totalEntryCount} 项，JSON ${report.validJsonCount} 项均有效"
        },
        definition(
            id = "local.app_registry",
            category = DeveloperTestCategory.LOCAL,
            title = "应用注册表",
            description = "检查应用入口 key、标题和分组是否完整且没有重复。",
            risk = DeveloperTestRisk.LOCAL_ONLY,
            timeoutMillis = LOCAL_TIMEOUT_MILLIS,
        ) {
            val apps = AppRegistry.all()
            check(apps.isNotEmpty())
            check(apps.map { it.key }.distinct().size == apps.size)
            check(apps.all { it.key.isNotBlank() && it.title.isNotBlank() && it.group.isNotBlank() })
            "${apps.size} 个应用入口，key 均唯一"
        },
        definition(
            id = "local.debug_clock",
            category = DeveloperTestCategory.LOCAL,
            title = "调试时钟",
            description = "检查统一时间入口的日期、分钟范围和格式化结果。",
            risk = DeveloperTestRisk.LOCAL_ONLY,
            timeoutMillis = LOCAL_TIMEOUT_MILLIS,
        ) {
            val minutes = DebugClock.currentMinutes()
            check(minutes in 0..1439)
            check(DebugClock.formatMinutes(minutes).matches(Regex("\\d{2}:\\d{2}")))
            if (DebugClock.isFrozen()) "时间覆盖生效中 · ${DebugClock.now()}" else "真实时间 · ${DebugClock.now()}"
        },
        definition(
            id = "local.assets",
            category = DeveloperTestCategory.LOCAL,
            title = "核心资源完整性",
            description = "校验应用图标与超星字体映射资源可读取，并检查映射表结构。",
            risk = DeveloperTestRisk.LOCAL_ONLY,
            timeoutMillis = LOCAL_TIMEOUT_MILLIS,
        ) {
            withContext(Dispatchers.IO) {
                val iconHeader = application.assets.open("ahu_plus_icon.png").use { stream ->
                    ByteArray(PNG_SIGNATURE.size).also { bytes ->
                        check(stream.read(bytes) == bytes.size) { "应用图标资源不完整" }
                    }
                }
                check(iconHeader.contentEquals(PNG_SIGNATURE)) { "应用图标不是有效 PNG" }
                val fontMapSize = application.assets.open("font_map_table.json").use { stream ->
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                        JsonParser.parseReader(reader).asJsonObject.size()
                    }
                }
                check(fontMapSize >= MIN_FONT_MAP_ENTRIES) { "字体映射条目不足" }
                "PNG 签名有效 · 字体映射 $fontMapSize 项"
            }
        },
        definition(
            id = "local.crypto",
            category = DeveloperTestCategory.LOCAL,
            title = "加密与 TLS 能力",
            description = "检查登录、摘要和安大兼容连接依赖的系统加密算法是否可用。",
            risk = DeveloperTestRisk.LOCAL_ONLY,
            timeoutMillis = LOCAL_TIMEOUT_MILLIS,
        ) {
            Cipher.getInstance("AES/CBC/PKCS5Padding")
            MessageDigest.getInstance("SHA-256")
            SSLContext.getInstance("TLSv1.2")
            "AES/CBC、SHA-256 与 TLS 1.2 均可用"
        },
        definition(
            id = "cas.session",
            category = DeveloperTestCategory.AUTHENTICATION,
            title = "统一身份认证会话",
            description = "校验 CAS 会话，必要时使用已保存凭据续期。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            ensureCasSession()
            "CAS 会话有效"
        },
        definition(
            id = "portal.balance",
            category = DeveloperTestCategory.CAMPUS_CARD,
            title = "门户一卡通余额",
            description = "校验门户会话并读取一次余额接口，不显示余额内容。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            ensureCasSession()
            application.cardRepository.getPortalBalance().getOrThrow()
            "余额接口响应有效"
        },
        definition(
            id = "jw.session",
            category = DeveloperTestCategory.AUTHENTICATION,
            title = "教务会话",
            description = "认证教务系统并检查共享 Cookie 会话。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            ensureJwSession()
            "教务会话有效"
        },
        definition(
            id = "jw.semesters",
            category = DeveloperTestCategory.ACADEMIC,
            title = "教务学期列表",
            description = "读取并解析当前账号可用的学期列表。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            ensureJwSession()
            countSummary("学期", application.courseRepository.getSemesterList().getOrThrow().size)
        },
        definition(
            id = "jw.schedule",
            category = DeveloperTestCategory.ACADEMIC,
            title = "课表",
            description = "读取默认学期课表并检查解析结果。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            ensureJwSession()
            val schedule = application.courseRepository.getSchedule().getOrThrow()
            countSummary("课程活动", schedule.activities.size)
        },
        definition(
            id = "jw.grades",
            category = DeveloperTestCategory.ACADEMIC,
            title = "成绩",
            description = "读取全部学期成绩并检查解析结果。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            ensureJwSession()
            val grades = application.gradeRepository.getGrades().getOrThrow()
            countSummary("成绩记录", grades.allGrades().size)
        },
        definition(
            id = "jw.exams",
            category = DeveloperTestCategory.ACADEMIC,
            title = "考试安排",
            description = "读取并解析当前账号的考试安排。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            ensureJwSession()
            countSummary("考试记录", application.examRepository.getExams().getOrThrow().size)
        },
        definition(
            id = "jw.training_plan",
            category = DeveloperTestCategory.ACADEMIC,
            title = "培养方案",
            description = "读取培养方案并检查顶层模块解析。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            ensureJwSession()
            val plan = application.trainingPlanRepository.getTrainingPlan().getOrThrow()
            countSummary("培养方案模块", plan.children.orEmpty().size)
        },
        definition(
            id = "jw.program_completion",
            category = DeveloperTestCategory.ACADEMIC,
            title = "培养方案完成度",
            description = "读取课程完成状态与汇总，不显示课程或学分内容。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            ensureJwSession()
            val courses = application.programCompletionRepository.getCompletionData().getOrThrow().first
            countSummary("完成度课程", courses.size)
        },
        definition(
            id = "student.info",
            category = DeveloperTestCategory.STUDENT_SERVICES,
            title = "学生一张表",
            description = "读取学生基本、住宿和学业预警字段，仅返回字段数量。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
            timeoutMillis = LONG_NETWORK_TIMEOUT_MILLIS,
        ) {
            ensureCasSession()
            val info = application.studentInfoRepository.getStudentInfo().getOrThrow()
            countSummary(
                "学生信息字段",
                info.basicFields.size + info.housingFields.size + info.academicWarningFields.size,
            )
        },
        definition(
            id = "student.finance",
            category = DeveloperTestCategory.STUDENT_SERVICES,
            title = "财务汇总",
            description = "读取六类财务数据，仅返回字段总数。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
            timeoutMillis = LONG_NETWORK_TIMEOUT_MILLIS,
        ) {
            ensureCasSession()
            val finance = application.financeRepository.getFinanceSummary().getOrThrow()
            val count = finance.scholarship.size + finance.grant.size + finance.hardshipGrant.size +
                finance.workStudy.size + finance.arrearsStatus.size + finance.loan.size
            countSummary("财务字段", count)
        },
        definition(
            id = "student.attendance",
            category = DeveloperTestCategory.STUDENT_SERVICES,
            title = "考勤记录",
            description = "读取考勤列表，仅返回记录数量。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
            timeoutMillis = LONG_NETWORK_TIMEOUT_MILLIS,
        ) {
            ensureCasSession()
            val summary = application.attendanceRepository.getAttendanceList(fullRefresh = false).getOrThrow()
            countSummary("考勤记录", summary.records.size)
        },
        definition(
            id = "ycard.bills",
            category = DeveloperTestCategory.CAMPUS_CARD,
            title = "一卡通账单",
            description = "认证 Ycard 并读取第一页账单，仅返回记录数量。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
            timeoutMillis = LONG_NETWORK_TIMEOUT_MILLIS,
        ) {
            val (username, password) = savedCasCredentials()
            application.ycardRepository.login(username, password).getOrThrow()
            val bills = application.ycardRepository.getBills(current = 1, size = 20).getOrThrow()
            countSummary("账单记录", bills.data?.records.orEmpty().size)
        },
        definition(
            id = "adwmh.session",
            category = DeveloperTestCategory.CAMPUS_CARD,
            title = "支付码会话",
            description = "校验现有支付码会话，不请求二维码或支付数据。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
            timeoutMillis = LONG_NETWORK_TIMEOUT_MILLIS,
        ) {
            if (!application.adwmhCardRepository.hasSession()) skip("未配置支付码会话")
            application.adwmhCardRepository.validateSession().getOrThrow()
            "支付码会话有效"
        },
        definition(
            id = "market.topics",
            category = DeveloperTestCategory.THIRD_PARTY,
            title = "校园集市帖子",
            description = "使用已保存身份读取第一页帖子，仅返回数量。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            val token = selectedMarketToken() ?: skip("未配置校园集市身份")
            val topics = application.marketRepository.getTopics(page = 1, identity = token).getOrThrow()
            countSummary("帖子", topics.size)
        },
        definition(
            id = "chaoxing.session",
            category = DeveloperTestCategory.THIRD_PARTY,
            title = "超星会话",
            description = "校验已保存的超星 Cookie 是否有效。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            if (!application.chaoxingRepository.isLoggedIn()) skip("未登录超星学习通")
            check(application.chaoxingRepository.validateSession())
            "超星会话有效"
        },
        definition(
            id = "chaoxing.courses",
            category = DeveloperTestCategory.THIRD_PARTY,
            title = "超星课程列表",
            description = "读取超星课程列表，仅返回课程数量。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            if (!application.chaoxingRepository.isLoggedIn()) skip("未登录超星学习通")
            val courses = application.chaoxingRepository.getCourseList().getOrThrow()
            countSummary("超星课程", courses.size)
        },
        definition(
            id = "welearn.courses",
            category = DeveloperTestCategory.THIRD_PARTY,
            title = "WeLearn 课程列表",
            description = "读取 WeLearn 课程列表，不发送学习心跳。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            if (!application.weLearnAuthRepository.isLoggedIn()) skip("未登录 WeLearn")
            val courses = application.weLearnRepository.getCourses().getOrThrow()
            countSummary("WeLearn 课程", courses.size)
        },
        definition(
            id = "cprog.transport",
            category = DeveloperTestCategory.THIRD_PARTY,
            title = "C 语言平台传输通道",
            description = "探测校内直连或 WebVPN 通道，不初始化验证码会话。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
            timeoutMillis = LONG_NETWORK_TIMEOUT_MILLIS,
        ) {
            application.cProgAuthRepository.prepareTransport().getOrThrow()
            "传输通道可用"
        },
        definition(
            id = "cprog.subjects",
            category = DeveloperTestCategory.THIRD_PARTY,
            title = "C 语言平台科目",
            description = "读取平台科目列表，不提交代码或试卷。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
            timeoutMillis = LONG_NETWORK_TIMEOUT_MILLIS,
        ) {
            if (!application.cProgAuthRepository.isLoggedIn()) skip("未登录 C 语言平台")
            val subjects = application.cProgRepository.getSubjects().getOrThrow()
            countSummary("平台科目", subjects.size)
        },
        definition(
            id = "evaluation.semesters",
            category = DeveloperTestCategory.ACADEMIC,
            title = "评教学期",
            description = "认证评教服务并读取学期下拉，不读取或提交问卷。",
            risk = DeveloperTestRisk.AUTHENTICATED_READ,
        ) {
            ensureJwSession()
            val semesters = application.evaluationRepository.getSemesters().getOrThrow()
            countSummary("评教学期", semesters.size)
        },
        definition(
            id = "public.jwc_notices",
            category = DeveloperTestCategory.PUBLIC_SERVICE,
            title = "教务处通知",
            description = "读取教务处公开通知列表，仅返回数量。",
            risk = DeveloperTestRisk.PUBLIC_READ,
        ) {
            val notices = application.jwcNoticeRepository.getNotices(limit = 6).getOrThrow()
            countSummary("通知", notices.size)
        },
        definition(
            id = "public.weather",
            category = DeveloperTestCategory.PUBLIC_SERVICE,
            title = "天气数据",
            description = "读取公开天气与空气质量接口并刷新本地缓存。",
            risk = DeveloperTestRisk.PUBLIC_READ,
        ) {
            application.weatherRepository.fetchRemote().getOrThrow()
            checkNotNull(application.weatherRepository.getCached())
            "天气数据已刷新"
        },
        definition(
            id = "public.announcements",
            category = DeveloperTestCategory.PUBLIC_SERVICE,
            title = "开发者公告",
            description = "读取公开公告源并校验本地解析结果。",
            risk = DeveloperTestRisk.PUBLIC_READ,
        ) {
            application.announcementRepository.fetchRemote().getOrThrow()
            countSummary("公告", application.announcementRepository.getAllAnnouncements().size)
        },
        definition(
            id = "public.exam_predictions",
            category = DeveloperTestCategory.PUBLIC_SERVICE,
            title = "考试预测数据源",
            description = "读取公开考试预测数据并校验本地解析结果，仅返回记录数量。",
            risk = DeveloperTestRisk.PUBLIC_READ,
            timeoutMillis = LONG_NETWORK_TIMEOUT_MILLIS,
        ) {
            withContext(Dispatchers.IO) {
                application.examDataRepository.fetchRemote().getOrThrow()
                val records = application.examDataRepository.getCachedExams()
                    ?: error("考试预测缓存未生成")
                countSummary("考试预测记录", records.size)
            }
        },
    )

    private fun definition(
        id: String,
        category: DeveloperTestCategory,
        title: String,
        description: String,
        risk: DeveloperTestRisk,
        timeoutMillis: Long = NETWORK_TIMEOUT_MILLIS,
        action: suspend () -> String,
    ) = Definition(
        test = DeveloperModuleTest(
            id = id,
            category = category,
            title = title,
            description = description,
            risk = risk,
        ),
        timeoutMillis = timeoutMillis,
        action = action,
    )

    private suspend fun ensureCasSession() {
        application.casAuthRepository.ensureValidSession().getOrThrow()
    }

    private suspend fun ensureJwSession() {
        application.jwAuthRepository.authenticate().getOrThrow()
    }

    private fun savedCasCredentials(): Pair<String, String> {
        val username = application.sessionManager.getUsername()?.takeIf(String::isNotBlank)
            ?: skip("未保存统一身份认证账号")
        val password = application.sessionManager.getPassword()?.takeIf(String::isNotBlank)
            ?: skip("未保存统一身份认证密码")
        return username to password
    }

    private fun selectedMarketToken(): String? {
        val selectedIds = application.marketRepository.getSelectedIdentityIds()
        val identities = application.marketRepository.getAllIdentities()
        val selected = identities
            .firstOrNull { it.id in selectedIds }
            ?.token
            ?.takeIf(String::isNotBlank)
        val firstConfigured = identities.firstOrNull()?.token?.takeIf(String::isNotBlank)
        val legacy = application.marketRepository.getSavedIdentity()?.takeIf(String::isNotBlank)
        return selected ?: firstConfigured ?: legacy
    }

    private fun countSummary(label: String, count: Int): String = "$label：$count 项"

    private fun skip(reason: String): Nothing = throw DeveloperTestUnavailableException(reason)

    private fun File.isUsableDirectory(): Boolean =
        exists() && isDirectory && canRead() && canWrite()

    companion object {
        private const val LOCAL_TIMEOUT_MILLIS = 5_000L
        private const val NETWORK_TIMEOUT_MILLIS = 45_000L
        private const val LONG_NETWORK_TIMEOUT_MILLIS = 90_000L
        private const val MIN_FONT_MAP_ENTRIES = 30_000
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
