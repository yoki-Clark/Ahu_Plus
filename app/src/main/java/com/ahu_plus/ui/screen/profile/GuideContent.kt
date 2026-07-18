package com.ahu_plus.ui.screen.profile

/**
 * 使用帮助文档的数据层（使用指南 + 常见问题 合并后的统一内容源）。
 *
 * 结构三层：[GuideCategory] → [GuideEntry] → [GuideSection]。
 *  - 一个「分类」对应文档里的一级标题（如 `# 基础功能`）
 *  - 一个「条目」对应二级标题（如 `### 登录与会话存留`）
 *  - 一个「小节」对应三级标题（如 `##### 使用方式`）
 *
 * 五种固定小节（[GuideSectionKind]）：使用方式 / 实现方式 / 出现的问题与解决 /
 * 尚未解决的问题 / 未来的计划。**不要求每个条目都填满**——内容为空的小节不会渲染。
 *
 * 「未来更新计划」页面是**自动聚合**的：扫描所有条目里 [GuideSectionKind.FUTURE]
 * 的小节生成路线图列表（见 [roadmapItems]），无需手动维护第二份清单。
 *
 * ─── 后续补内容只需改这个文件 ───
 * 用 [usage] / [impl] / [problems] / [unresolved] / [future] 这几个 builder，
 * 配合 `p("段落")` 和 `b("无序列表项")` 撰写正文。示例见文件末尾的 [guideCategories]。
 */

/** 五种标准小节类型；label 渲染为小节标题，emoji 作前缀图标，desc 用于首开弹窗的说明。 */
enum class GuideSectionKind(val label: String, val emoji: String, val desc: String) {
    USAGE("使用方式", "📖", "这个功能怎么用、入口在哪"),
    IMPLEMENTATION("实现方式", "🔧", "背后是怎么实现的"),
    PROBLEMS("出现的问题与解决", "🐛", "开发时踩过的坑及解决办法"),
    UNRESOLVED("尚未解决的问题", "⚠️", "目前还存在、暂未解决的问题"),
    FUTURE("未来的计划", "🚀", "未来可能会添加或改进的方向"),
}

/** 正文里的一个块：段落或无序列表项。 */
sealed interface GuideBlock {
    data class Para(val text: String) : GuideBlock
    data class Bullet(val text: String) : GuideBlock
}

/**
 * 一个小节。
 * @param kind 小节类型。
 * @param heading 自定义标题；为 null 时用 [kind] 的默认 label。
 *   仅在一个条目里需要并列多个同类小节时使用（如「计划接入的第三方平台」下每个平台一节，
 *   kind=FUTURE、heading=平台名）。
 * @param body 正文块列表；为空则该小节不渲染。
 */
data class GuideSection(
    val kind: GuideSectionKind,
    val heading: String? = null,
    val body: List<GuideBlock>,
) {
    /** 渲染用的小节标题：自定义优先，否则用类型默认 label。 */
    val displayLabel: String get() = heading ?: kind.label
    val isEmpty: Boolean get() = body.isEmpty()
}

/**
 * 一个功能条目。
 * @param id 稳定的导航键，路线图跳转用，**全局唯一**。
 * @param title 条目标题（如「登录与会话存留」）。
 * @param summary 列表里显示的一句话简介（可空）。
 * @param sections 小节列表；渲染时自动跳过空小节。
 */
data class GuideEntry(
    val id: String,
    val title: String,
    val summary: String? = null,
    val sections: List<GuideSection>,
) {
    val visibleSections: List<GuideSection> get() = sections.filterNot { it.isEmpty }
    /** 该条目下的「未来的计划」小节（供路线图聚合）。 */
    val futureSections: List<GuideSection>
        get() = sections.filter { it.kind == GuideSectionKind.FUTURE && !it.isEmpty }
}

/** 一个一级分类。 */
data class GuideCategory(
    val title: String,
    val entries: List<GuideEntry>,
)

/** 路线图（未来更新计划）里的一行；由 [roadmapItems] 自动生成。 */
data class RoadmapItem(
    val entryId: String,
    /** 显示标题：自定义小节标题优先（如平台名），否则用条目标题。 */
    val label: String,
    /** 该条目所属分类标题，作副标题展示来源。 */
    val categoryTitle: String,
    /** 摘要：未来小节的首段文本。 */
    val summary: String,
)

// ──────────────────────────────────────────────────────────────────────
//  内容撰写 DSL —— 后续补文档时主要和这一段打交道
// ──────────────────────────────────────────────────────────────────────

class BodyBuilder {
    val blocks = mutableListOf<GuideBlock>()
    /** 段落。 */
    fun p(text: String) { blocks += GuideBlock.Para(text) }
    /** 无序列表项（渲染为「• …」）。 */
    fun b(text: String) { blocks += GuideBlock.Bullet(text) }
}

private fun body(init: BodyBuilder.() -> Unit): List<GuideBlock> =
    BodyBuilder().apply(init).blocks

fun usage(init: BodyBuilder.() -> Unit) =
    GuideSection(GuideSectionKind.USAGE, null, body(init))

fun impl(init: BodyBuilder.() -> Unit) =
    GuideSection(GuideSectionKind.IMPLEMENTATION, null, body(init))

fun problems(init: BodyBuilder.() -> Unit) =
    GuideSection(GuideSectionKind.PROBLEMS, null, body(init))

fun unresolved(init: BodyBuilder.() -> Unit) =
    GuideSection(GuideSectionKind.UNRESOLVED, null, body(init))

/** 未来计划小节。[heading] 用于「计划接入的第三方平台」下区分各平台名。 */
fun future(heading: String? = null, init: BodyBuilder.() -> Unit) =
    GuideSection(GuideSectionKind.FUTURE, heading, body(init))

// ──────────────────────────────────────────────────────────────────────
//  路线图聚合 —— 扫描所有 FUTURE 小节，无需手工维护
// ──────────────────────────────────────────────────────────────────────

/** 把所有条目的「未来的计划」小节摊平成路线图列表，保持文档出现顺序。 */
val roadmapItems: List<RoadmapItem> by lazy {
    buildList {
        guideCategories.forEach { category ->
            category.entries.forEach { entry ->
                entry.futureSections.forEach { section ->
                    val firstPara = section.body
                        .filterIsInstance<GuideBlock.Para>()
                        .firstOrNull()?.text
                    val firstBullet = section.body
                        .filterIsInstance<GuideBlock.Bullet>()
                        .firstOrNull()?.text
                    add(
                        RoadmapItem(
                            entryId = entry.id,
                            // 平台名等自定义标题优先，否则用条目名
                            label = section.heading ?: entry.title,
                            categoryTitle = category.title,
                            summary = firstPara ?: firstBullet ?: "",
                        )
                    )
                }
            }
        }
    }
}

/** 按 id 查条目（详情页 / 路线图跳转用）。 */
fun findGuideEntry(id: String): Pair<GuideCategory, GuideEntry>? {
    guideCategories.forEach { category ->
        category.entries.forEach { entry ->
            if (entry.id == id) return category to entry
        }
    }
    return null
}

// ──────────────────────────────────────────────────────────────────────
//  搜索
// ──────────────────────────────────────────────────────────────────────

/**
 * 一条搜索命中：[entry] 命中关键字，[snippet] 是首个匹配的原文片段（标题 > 摘要 > 正文顺序），
 * [matchStart]/[matchLength] 标记 [snippet] 内匹配位置，供 UI 截断+高亮。
 */
data class GuideSearchHit(
    val category: GuideCategory,
    val entry: GuideEntry,
    val snippet: String,
    val matchStart: Int,
    val matchLength: Int,
)

/**
 * 在所有条目中按 query 搜索；命中优先级 title > summary > 第一个匹配的 body 段落；
 * 不区分大小写；保持文档声明顺序。
 */
fun searchGuideEntries(query: String): List<GuideSearchHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val needle = q.lowercase()
    return buildList {
        guideCategories.forEach { category ->
            category.entries.forEach { entry ->
                findFirstHit(entry, needle)?.let { (text, idx) ->
                    add(
                        GuideSearchHit(
                            category = category,
                            entry = entry,
                            snippet = text,
                            matchStart = idx,
                            matchLength = needle.length,
                        )
                    )
                }
            }
        }
    }
}

/** 返回首个命中片段及其在原文中的下标；找不到返回 null。 */
private fun findFirstHit(entry: GuideEntry, needle: String): Pair<String, Int>? {
    entry.title.lowercase().indexOf(needle).takeIf { it >= 0 }?.let {
        return entry.title to it
    }
    entry.summary?.lowercase()?.indexOf(needle)?.takeIf { it >= 0 }?.let {
        return entry.summary to it
    }
    entry.sections.forEach { section ->
        section.body.forEach { block ->
            val text = when (block) {
                is GuideBlock.Para -> block.text
                is GuideBlock.Bullet -> block.text
            }
            text.lowercase().indexOf(needle).takeIf { it >= 0 }?.let {
                return text to it
            }
        }
    }
    return null
}

// ──────────────────────────────────────────────────────────────────────
//  内容种子 —— ⚠️ 后续由维护者补写完整文档
//
//  说明：目前 `使用方式` 多数迁移自旧版「使用指南」，`实现方式` 仅就已知信息
//  简述，`出现的问题 / 尚未解决 / 未来的计划` 大多留空（空小节不渲染）。
//  补内容时直接在对应条目里加 impl{}/problems{}/unresolved{}/future{} 即可。
// ──────────────────────────────────────────────────────────────────────

val guideCategories: List<GuideCategory> = listOf(

    // ═══════════════════════ 基础功能 ═══════════════════════
    GuideCategory(
        title = "基础功能",
        entries = listOf(
            GuideEntry(
                id = "login",
                title = "登录与会话存留",
                summary = "学号 + 智慧安大密码登录，自动打通多个校园系统",
                sections = listOf(
                    usage {
                        p("填上学号，密码用你登智慧安大（网上办事大厅）的那个，点登录就行。密码框右边有个小眼睛，可以点开看看有没有输错。")
                        p("登录一次，校园卡、教务、个人信息这些系统就都自动通了，平时不用反复登。下次打开 App 会自动帮你接着用上次的登录状态，中途会看到安大+ 图标轻轻呼吸的等待动画；万一自动登录失败，点屏幕任意位置就能重试，也能在那一页切换账号。")
                    },
                    impl {
                        p("登录页 LoginScreen 把学号/密码交给 LoginViewModel → CasAuthRepository 走安大 CAS 统一身份认证（GET 登录页取 lt → DES 加密 username+password+lt → POST /cas/device → POST /cas/login 拿 CASTGC，再用 ST 逐个换取教务 SESSION、一卡通 JSESSIONID、学生一张表等下游会话）。")
                        p("二次启动走 AutoLoginScreen + AutoLoginViewModel，状态机 Loading / Success / Failed / NoCredentials：有凭据自动重登跳主页，无凭据跳登录页，失败整页可点重试。各系统会话凭据持久化在 DataStore，SessionAuthenticator（OkHttp Authenticator + HTML 嗅探 Interceptor 双层）在请求掉登录态时被动续期。安大+ 图标走 Coil 渲染 assets/ahu_plus_icon.png。")
                    },
                    problems {
                        p("重认证一度会误调 clearAll()，把集市 Token 等用户手动配置一起清掉。已收敛为：仅「无本地缓存且认证失败」才走轻量重认证，绝不清空用户配置。")
                    },
                    unresolved {
                        b("登录假设无验证码；首次在新设备绑定时安大可能要求微信验证码，这步未做自动化，需手动完成。（其实只要你在微信里面登过，就不会触发这个）")
                        b("会话保活是被动续期、无主动 ping，长时间不用偶尔要重认证一次。")
                    },
                    future {
                        p("可能支持手机短信验证码登录（暂未开始分析接口）。")
                    },
                ),
            ),
            // ##APPEND##
            GuideEntry(
                id = "dashboard",
                title = "工作台（首页）",
                summary = "今日课程、日程、最近使用、教务通告一屏聚合",
                sections = listOf(
                    usage {
                        p("进 App 第一个页面就是工作台，从上往下依次是：")
                        b("今日课程卡：显示今天要上的课、时间地点老师，临近的课会标「N 分钟后 / 已开始」倒计时；在上课时能一键加今日作业")
                        b("日程卡：列出今天起最近几条日程（课程、考试、作业和自己加的日程），整卡点进「日程」页看全部，＋号直接加日程")
                        b("收藏应用：把你最常用的功能 pinned 到首页，最多 6 个，按 2 行 × 3 列排布。没收藏时点「添加常用应用」打开选择面板；有收藏时点标题栏「编辑」进入编辑模式，也可以直接长按任意图标一步进入编辑并开始拖拽排序。编辑模式下图标抖动提示，被拖拽的图标以浮层跟手移动，松开后位置保存；尾部「＋」可继续添加，点图标上的 × 移除。右侧「更多」进完整应用中心。")
                        b("教务通告：教务处的通知，点一条直接展开看正文，「更多」看全部")
                        b("右上角刷新按钮一次性刷新课程和通告")
                    },
                    impl {
                        p("DashboardScreen 复用 ScheduleViewModel，今日课程经 CourseRepository.toDisplayItems() 按 currentWeek 过滤 weekIndexes（避免「今天 weekday 有课但本周不上」误显示）。倒计时 chip 用 DebugClock.nowTime() + 每 30s tick 重算。日程卡数据来自 AgendaViewModel.eventsByDate（AgendaBuilder 把课表/考试展开成带具体日期的事件，合并 HomeworkRepository 作业 + UserTaskRepository 手动日程，按 LocalDate 分组）。「最近使用」由 AppRegistry.pickRecent() 取最近 3 个、onRecordApp 落点击记录。教务通告由 JwcNoticeRepository 通过 OkHttp 获取列表、分页和详情，并用 Jsoup 解析；仅在服务端返回 412 时临时启动 JwcWafBootstrap 获取 WAF Cookie，验证后持久化约 6 天并自动重试原操作，附件下载复用同一 Cookie。")
                    },
                ),
            ),
            GuideEntry(
                id = "apphub",
                title = "应用中心",
                summary = "所有功能的总入口，分组陈列",
                sections = listOf(
                    usage {
                        p("工作台「收藏应用 → 更多」进来，所有功能按「学习 / 通知 / 校园卡 / 生活 / 个人信息」分组列好：课表、成绩、考试、培养方案进度、空教室、教务通知、消费账单、消费分析、浴室、空调、照明、网费、学生信息、财务汇总、考勤记录。点哪个进哪个，返回回到这里。")
                    },
                    impl {
                        p("AppHubScreen 自带页面栈（currentPage + BackHandler），所有子页 ViewModel 由外部注入共享实例，不重新加载。我的信息二级页（基本信息/住宿/学业预警）返回回到 MyInfoHub 而非直接退出。排考预测页延迟到进入时才 remember { ExamPredictionViewModel(...) } 触发首拉。")
                    },
                ),
            ),
        ),
    ),

    // ═══════════════════════ 教务学业 ═══════════════════════
    GuideCategory(
        title = "教务学业",
        entries = listOf(
            GuideEntry(
                id = "schedule",
                title = "课表",
                summary = "学期课程表，可调外观、查详情、加作业、设提醒",
                sections = listOf(
                    usage {
                        p("进去就是本周课表。顶部左右箭头或左右滑动切换周次，下拉可以刷新，点击周次可以跳到指定周，＋号可以手动添加自定义课程。")
                        p("点一节课的格子展开详情，里面有五块内容：课程详情（学分、代码、上课时间）、考核方案（考试/考查及成绩比例，这个需要你自己填，会保存在你本地）、课程备注（用来给你写对于这门课的一些备注）、这节课的备注（针对该时段的单独笔记，比如这节课布了作业，这节课点名了）、考勤记录（历次签到/缺勤）。")
                        p("右上角齿轮进设置：外观（列宽、行高、字体缩放三个滑块，可恢复默认）、显示（展示周六/周日、左右滑动切换周、进入自动跳本周、显示其他学期）、课程提醒（开关 + 设置提前几分钟）。")
                    },
                    impl {
                        p("ScheduleScreen + WeekGrid + WeekPager（分页时包 HorizontalPager）。ScheduleViewModel → CourseRepository 从教务 SSO（jw.ahu.edu.cn）拉课表，学期列表解析课程表 HTML 中 select#allSemesters 的 option，JSON 缓存到 DataStore。每 60s 一次 timeTick 驱动 WeekGrid 重算时间横线 currentTimeLineY。详情 Sheet 按五个 CollapsibleSection 渲染，考核方案由 AssessmentRepository 单独拉取按课程代码对应。自定义课程 UserScheduleItem 仅存本地、不上传教务。")
                    },
                ),
            ),
            GuideEntry(
                id = "grade",
                title = "成绩",
                summary = "多学期成绩 + GPA 汇总 + 色阶柱状图",
                sections = listOf(
                    usage {
                        p("顶部横向滚动的标签切换学期，每学期一张 GPA 摘要卡（均分、加权 GPA、学分 GPA），下方是各课成绩；点课程行弹底部窗看评分分布柱状图（90 以上、80-89、70-79、60-69、不及格各占多少，颜色区分）。")
                    },
                    impl {
                        p("GradeViewModel → GradeRepository 调教务成绩 JSON API，响应附带 GpaMetadata（各学期加权 GPA / 平均分 / 学分 GPA）。柱状图用 DrawScope.drawRoundRect 纯 Canvas 绘制，色段红/橙/绿/蓝/灰对应分数档。")
                    },
                ),
            ),
            // ##APPEND2##
            GuideEntry(
                id = "exam",
                title = "考试安排",
                summary = "考试列表，已结束折叠、一键加系统日历",
                sections = listOf(
                    usage {
                        p("列出本学期所有考试，每场显示课程名、时间、考场、座位号（有才显示），左侧彩色竖条区分类型（补考红、缓考橙、正常蓝）。已结束的默认折叠在「已结束」分区，展开后整体变灰。")
                        p("每张卡右上角有日历图标，点一下弹确认窗，确认后跳转系统日历 App 新建事件，时间/地点/座位号自动填好。顶部「预测」按钮进排考预测页。")
                    },
                    impl {
                        p("ExamRepository 解析教务 HTML 考试页面。已结束判断 isExamFinished() 解析 yyyy-MM-dd HH:mm~HH:mm 取结束时间与 DebugClock.nowMillis() 比较。加日历 buildCalendarIntent() 用正则解析起止时间构建 Intent.ACTION_INSERT + CalendarContract.Events.CONTENT_URI，解析失败降级为全天事件；类型颜色按字符串含「补」/「缓」判断。")
                    },
                ),
            ),
            GuideEntry(
                id = "exam_prediction",
                title = "排考预测",
                summary = "零登录拉公开数据，按课程号精确匹配，老师匹配优先",
                sections = listOf(
                    usage {
                        p("顶部信息卡显示数据生成时间、本机上次更新、匹配到几门课几场考试，以及当前是实时还是缓存。")
                        p("结果按课程聚合成卡片，每门课一张，点击展开所有场次；同一门课若能匹配到你的任课老师，对应场次亮出「⭐ 你的老师」徽章并排最前。拉取失败时显示缓存并标「展示本地缓存」。")
                    },
                    impl {
                        p("ExamDataRepository.fetchRemote() 从 yao-enqi/ahu-plus-update Gitee 公开 JSON 拉取，ResilientDns 自定义 Dns 过滤 198.18.x.x 伪 IP 绕开 DNS 污染。用 courseCode 与 SessionManager 课表精确匹配，再做教师名宽松匹配（MATCH_TYPE_TEACHER），parseProctorNames() 解析巡考教师串。结果缓存到 DataStore。")
                    },
                ),
            ),
            GuideEntry(
                id = "empty_classroom",
                title = "空教室",
                summary = "校区/楼/层筛选，时间轴可视化，「连续空闲」排序",
                sections = listOf(
                    usage {
                        p("依次点校区→教学楼→楼层（全部或具体层），结果自动刷新。日期默认今天，「明天」「后天」快捷按钮一点就切，也可点日历图标选未来 30 天内任意日期。")
                        p("每间教室卡显示教室名、类型、楼层、座位数，右侧彩色标签写「空闲 N 节（M 段）」；底部一条可视化时间条，绿格是空闲节次，今天的当前节次有蓝竖线+三角箭头指示。")
                        p("右上方「连续空闲」开关：开启后按最长连续空闲段重排序，标签变成「连坐 N 节 (全天 M)」，方便找能连续待几节的教室。")
                    },
                    impl {
                        p("EmptyClassroomRepository 请求教务空教室 API，结果封装 FreeRoomResult（freeSegments = AhuUnitTimes.collapseToSegments() 合并连续节次）。连续空闲排序：setContinuousFree() → filteredRooms 按 continuousFreeCount()（从当前节往后最长连续 free 段长）降序。时间条 FreeTimeBar 用 Canvas 画 13 节方块，nowLineFraction 在当前节内插值定位蓝线，60s tick 刷新。DatePicker 强制 Locale.SIMPLIFIED_CHINESE 保证星期头单字。")
                    },
                ),
            ),
            GuideEntry(
                id = "training_plan",
                title = "培养方案",
                summary = "专业培养方案 + 完成进度可视化",
                sections = listOf(
                    usage {
                        p("看你们专业的课程树，每个模块可展开/折叠，每门课有完成状态（✅已通过/⏳进行中/❌未通过/⬜未选）、学分要求、课程类型（理论/实验/实践）、考试方式标签。顶部进度条统计已完成/总需学分。")
                    },
                    impl {
                        p("TrainingPlanRepository 拉培养方案树 JSON（PlanModuleNode 树形），ProgramCompletionRepository 拉选课完成情况（CompletionCourse），按 courseCode 合并得状态。LinearProgressIndicator 展示整体进度，行色已过绿/进行中橙/未通过红/未选灰。")
                    },
                ),
            ),
        ),
    ),

    // ═══════════════════════ 校园生活 ═══════════════════════
    GuideCategory(
        title = "校园生活",
        entries = listOf(
            GuideEntry(
                id = "card",
                title = "校园卡",
                summary = "一卡通余额、智慧安大支付码、水电网费余额与充值、消费账单",
                sections = listOf(
                    usage {
                        p("在「校园卡」页面可以看到：")
                        b("一卡通余额（实时查）")
                        b("智慧安大支付码：有倒计时环（45 秒刷一次），点二维码可放大全屏；支付码右下角有刷新按钮")
                        b("水电网费余额：浴室、空调、照明、网费余额卡片，点「充值」输入金额和 6 位查询密码即可在线充值（查询密码默认从学生信息自动填）")
                        p("支付码有离线兜底：服务器抖动或限流时，会先用上次成功取到的码（10 分钟内）撑住展示，避免白屏；超过 60 秒的码会被标可能失效，卡片和全屏弹窗都会弹琥珀色提醒，可以照常试刷也可以手动刷新。")
                        p("点消费账单进账单列表，再点「消费分析」进详细分析页。")
                    },
                    impl {
                        p("一卡通余额 CardRepository（one.ahu.edu.cn，CAS → JSESSIONID，自签名 TLS 信任）。支付码 AdwmhCardRepository（adwmh.ahu.edu.cn）：仅接受 TLS 1.2，Tls12OnlySocketFactory 强制协议版本 + 约 2-3 次/分钟速率限制冷却；二维码用 QrCodeBitmap.create(payload, 720) 生成 720px Bitmap。浴室/电费/网费走 YcardRepository / YcardPayRepository，复用 blade-pay/pay 三步流程下单→拉码→提交，其中空调/照明共用 feeitemid=488，充值 sheet 会让用户先选充哪个。房间配置持久化 ElectricityRoomConfig DataStore。")
                    },
                ),
            ),
            GuideEntry(
                id = "card_analytics",
                title = "消费分析",
                summary = "按月或按学期统计消费，折线图 + 分类 + 商户排行 + 餐次分布",
                sections = listOf(
                    usage {
                        p("分「按月」「按学期」两种周期，顶部 Chip 切换；选好周期后用下拉选择具体的月份或学期。")
                        p("每个周期显示：总消费金额、日均消费、日消费趋势折线图；下方是消费分类排行（餐饮/超市/其他等，带进度条）、商户排行 Top N、餐次分布（早午晚各占几次、各花多少）。")
                    },
                    impl {
                        p("所有计算在客户端本地完成，BillRecord 列表经 toAnalyticsReport() 聚合生成 CardAnalyticsSummary（DailyPoint 趋势、CategoryStat 分类、MerchantStat 商户、FoodSplitStat 餐次）。折线图用 Canvas Path + drawPath(Stroke) 绘制，AnalyticsPeriodKind.MONTH（按月）和 SEMESTER（按学期）切换对应不同的 periods 列表。")
                    },
                ),
            ),
            // ##APPEND3##
            GuideEntry(
                id = "utilities",
                title = "水电网费",
                summary = "浴室/空调/照明/网费余额、账单明细与在线充值",
                sections = listOf(
                    usage {
                        p("这部分已经实现了自动填充，只需要更新「我的信息」即可实现填充相关需求信息。在「校园卡」页已有余额摘要，点卡片上的「充值」可直接充值；点进各项可看账单明细：")
                        b("浴室：余额 + 账单列表，可在详情页修改绑定手机号，充值时输入金额和 6 位查询密码")
                        b("空调/照明：余额 + 账单列表，支持按时间范围筛选（近 1 个月/3 个月/6 个月）；若空调/照明共用一个缴费项，充值 sheet 会先让你选充哪个")
                        b("网费：余额 + 账单，显示本期已用流量和时长，已接入 blade-pay 在线充值")
                    },
                    impl {
                        p("FinanceRepository 汇总各余额，BathroomUtilityDetailScreen/ElectricityUtilityDetailScreen/InternetUtilityDetailScreen 独立页面；电费账单按 ElectricityBillRange 枚举的日期范围发请求。充值复用 DepositSheet，经 YcardPayRepository 走 blade-pay/pay 下单→拉码→提交三步，InternetBalanceData 通过 @SerializedName 对齐 API 字段。")
                    },
                ),
            ),
            GuideEntry(
                id = "attendance",
                title = "考勤记录",
                summary = "学校刷卡考勤记录列表",
                sections = listOf(
                    usage {
                        p("查看学校考勤系统里记录的刷卡/签到记录，按时间倒序排列，每条显示课程名、日期时间、考勤状态。")
                    },
                    impl {
                        p("KqAttendanceRepository 从 tp_ep_stu 接口拉 KqAttendanceRecord 列表，与 StudentTableClient 共用同一个公共客户端，CAS → JSESSIONID 认证，结果缓存到 DataStore。")
                    },
                ),
            ),
            GuideEntry(
                id = "weather",
                title = "天气",
                summary = "Open-Meteo 公开数据,当前位置合肥蜀山区",
                sections = listOf(
                    usage {
                        p("从「应用中心 → 校园生活」或工作台「最近使用」进。顶部当前气温+体感+大图标+湿度/风速；下方一张空气质量卡(PM2.5/PM10/AQI + 健康建议，PM2.5>75 时显示红色「污染较高，建议戴口罩」)；")
                        p("再往下是未来 24 小时横滚条(逐小时图标+温度+降雨概率的蓝色%)，以及未来 5 天卡片(每天图标+文案+最低/最高温)。右下角显示更新时间（「刚刚」/「N 分钟前」/「N 小时前」），右上角按钮可手动刷新。")
                        p("点降雨概率那个蓝色数字会弹说明框：它是 NOAA GFS 模型给出的当小时降雨概率 (PoP)，不是湿度，越高越可能下雨，和下多大、下多久无关。")
                        p("另一处出现在课程提醒：天气严重(雨/雪)或空气污染时，课表上对应课程会加「带伞」「戴口罩」徽章。")
                    },
                    impl {
                        p("数据源 Open-Meteo 公开 API（零登录）：api.open-meteo.com/v1/forecast（current + hourly + 5 天 daily）+ air-quality-api.open-meteo.com/v1/air-quality（PM2.5/PM10/AQI）。")
                        p("模型固定 gfs_seamless：默认的 best_match ensemble 会把 ECMWF IFS025 的「最严重码」选出来，导致合肥天天报雷暴/雷暴冰雹，严重失真；GFS Seamless 在国内 5 日预报更克制，与中国天气/彩云一致得多。cma_grapes_global 虽然是中国气象局原生，但只支持 3-4 天。")
                        p("位置固定合肥市蜀山区（安徽大学本部，31.82/117.39），不可切换。两端点串行拉取，任一成功就保留那一部分的缓存，不互相破坏。")
                        p("网络照搬 AnnouncementRepository：策略 A HTTP/1.1 + COMPATIBLE_TLS + ResilientDns 手动 302，策略 B OkHttp 默认 HTTP/2 + MODERN_TLS 兜底——绕开部分国产 ROM（vivo 等）的 HTTP/2/TLS 协商问题。")
                        p("WeatherManager 单例 + StateFlow 持有最新 WeatherFeed：进程启动先把缓存喂进 _feed，MainActivity 进入首页时触发 1h Coroutine 循环 refresh；WeatherRepository.getCached() 同步读 SessionManager.WEATHER_JSON_KEY，WeatherFeed 合并两端点 raw JSON（含 WeatherCurrent/WeatherHourly/WeatherDaily + WeatherAirQuality）+ fetchedAt。")
                        p("课程天气提醒：按 startUnit/endUnit 查 unitTimes 拿起止分钟，对齐整点去 hourly.weatherCode 取上下课时 weatherCode，severity≥2 加「带伞」，PM2.5>75 加「戴口罩」，取起止 max 严重度。WMO 天气码映射在 WeatherCode.severity 0-4：0/1 晴阴、45/48 雾、51-55 毛毛雨、61-67 雨/冻雨、71-77 雪/雪粒、80-82 阵雨、85-86 阵雪、95-99 雷暴。")
                    },
                    problems {
                        b("best_match ensemble 把 ECMWF 雷暴冰雹过激选出来 → 模型改 gfs_seamless，与中国天气/彩云一致得多。")
                        b("国产 ROM HTTP/2 + TLS 协商挂 → 策略 A HTTP/1.1 + COMPATIBLE_TLS + ResilientDns，OkHttp 默认走不通时兜底。")
                    },
                    unresolved {
                        b("位置固定合肥蜀山区，不支持切换其他城市。")
                        b("数据由 Open-Meteo 第三方提供，可能与中国天气网略有偏差。")
                    },
                ),
            ),
            GuideEntry(
                id = "student_info",
                title = "个人信息与学籍",
                summary = "学校登记的学生信息，分三大块：基本信息、住宿数据、学业预警",
                sections = listOf(
                    usage {
                        p("在「应用 → 学生信息」进入汇总页，分三个入口：")
                        b("学生基本信息（姓名、学号、院系、专业、班级等）")
                        b("住宿数据（宿舍楼栋、房间号等）")
                        b("学业预警信息（成绩预警相关记录）")
                        p("每个详情页显示字段名+值的列表，顶部有数据最后更新时间，右上角可刷新。另外「我的」页有财务汇总（奖助学金/缴费记录）入口。")
                    },
                    impl {
                        p("StudentInfoRepository 调 tp_ep_stu getList 接口（23 字段），由 StudentTableClient 统一管理 JSESSIONID，部分字段做码值转换（性别代码→男/女等），全量分页获取。字段按 StudentInfo.basicFields/housingFields/academicWarningFields 分组。FinanceRepository 独立拉奖助学金/缴费记录。")
                    },
                ),
            ),
        ),
    ),

    // ═══════════════════════ 第三方服务 ═══════════════════════
    GuideCategory(
        title = "第三方服务",
        entries = listOf(
            GuideEntry(
                id = "market",
                title = "校园集市",
                summary = "需配置微信小程序身份凭证，看帖发帖、热榜、多校切换",
                sections = listOf(
                    usage {
                        p("首次使用要先填一个身份凭证才能访问集市。凭证从微信「集市」小程序里抓出来（一段长字符串），进「集市」tab 后在顶部右上角的齿轮图标里找到「集市设置 → API 身份字段」粘贴保存。配置好之后：")
                        b("刷帖子：单列或双列瀑布流，下拉刷新，顶部可搜索、可按板块筛选。帖子里的多图可以左右滑动翻看，带小圆点指示器；点单张图进全屏预览，左右滑切换、点黑边关闭")
                        b("看帖子：正文、图片、评论都有，评论也支持显示图片；可以评论回复，还能把整帖导出成长图分享")
                        b("发帖子：右下角＋按钮，选板块、可匿名、可选发帖校区")
                        b("热榜：看当前热门帖（仅单账号模式可用）")
                        b("消息：看回复/通知（仅单账号模式可用）")
                        b("多校：在集市设置里加多个凭证切换不同学校的集市")
                        b("返回顶部：列表滑出一定距离后右侧会出现「回到顶部」按钮，长按可拖动换位置，点一下回到顶部并触发刷新。若不想显示可在集市设置里关闭")
                        p("凭证大概 30 天失效，失效时会提示「身份字段已失效」，重新抓一次更新即可。")
                        p("凭证的具体获取方式：去仓库下载 tool 文件夹中的集市 token 获取工具，运行那个 cmd 文件即可。")
                    },
                    impl {
                        p("MarketRepository 调集市 API（api.zxs-bbs.cn，标准 HTTPS、trustAll=false），认证用 Authorization: Bearer <jwt>，凭证由用户手动粘贴（小程序登录走微信 code2session 锁在沙箱内，App 内无法自动获取）。列表用 LazyVerticalStaggeredGrid 实现瀑布流。导出长图见 MarketExportUtils（把帖子内容渲染到离屏 Composable/Canvas 再导出 Bitmap）。多校模式存多份 MarketIdentity，热榜与消息接口仅在单账号上下文可用。")
                        p("帖子图片：多张时用 HorizontalPager 实现卡片内左右滑动，单张/多张均点击进入 MarketImagePreview 全屏浏览；评论图片由 CommentImageGrid 按 1/2/3+ 张数做不同布局。返回顶部按钮 DraggableScrollToTopButton 用 pointerInput detectDragGestures 实现长按拖动，位置 rememberSaveable 持久化，点击触发 scrollToTop + refresh。")
                    },
                    problems {
                        p("Bearer 失效：拦截 401/403 → 提示「身份字段已失效」引导重新抓包。")
                    },
                    unresolved {
                        b("App 内无法自动登录集市，只能手动从微信小程序抓凭证，门槛较高。")
                        b("热榜、消息在多校模式下不可用（接口绑定单一身份）。")
                    },
                ),
            ),
            GuideEntry(
                id = "chaoxing",
                title = "学习通（超星）",
                summary = "课程 / 自动刷课 / 自动答题 / 签到 / 消息 / 作业",
                sections = listOf(
                    usage {
                        p("用手机号和密码登录超星学习通（跟 App / 网页版学习通同一账号）。")
                        b("课程：看课程列表和每章任务点完成情况；不想看的课长按可以隐藏，在设置页里管理")
                        b("自动刷课：开了之后 App 在后台帮你看视频、读资料、完成音频和直播任务；可以调视频倍速（设置里用滑块）；有悬浮窗实时显示进度（需要单独授权悬浮窗权限，不授权就只有通知）")
                        b("自动答题：遇到题目自动查答案，多个题库轮流找，也能接 AI（默认 DeepSeek，可切换 9 家平台）；题库顺序可以拖动调整")
                        b("签到：进课程点「立即签到」，App 自动识别类型并完成；支持普通、手势（九宫格画格子）、拍照（可预设默认图）、位置（可预设坐标和地址）、二维码、签到码 6 种（这个功能还没做实际测试，主要我本人课程没有用学习通签到的）")
                        b("消息：收件箱 + 课程活动")
                        b("作业：查看作业详情（暂时不能提交）")
                    },
                    impl {
                        p("登录 ChaoxingRepository 用 AES/CBC 加密手机号密码，Cookie 持久化。正文字体加密：CxFontDecoder 启动时加载 font_map_table.json（3 万+ 字形映射），TtfGlyphParser 纯 Kotlin 解析 TTF glyf 表，逐字形 MD5 反查汉字。")
                        p("题库回退链 ChaoxingTikuRepository：CACHE → YANXI → GO → LIKE → ADAPTER → AI → SILICONFLOW；normalizeAnswer() 标准化（单选取字母、多选模糊匹配、判断映射 true/false）。")
                        p("学习请求由 ChaoxingTrafficGovernor 按账号和主机串行；403、429、验证码或限制页会停止当前批次，并按 Retry-After 冷却。")
                        p("视频进度只按服务端 reportTimeInterval 上报按真实经过时间计算的进度；不支持瞬间完成、自动签到、刷访问次数或伪装浏览器指纹。")
                        p("签到 sign/SignFlowDialog：preSign 接口类型标记不可靠，改用响应内容关键字二次校验；GesturePad 九宫格手势绘制；位置签到读 LocationPicker 预设坐标。")
                    },
                    problems {
                        b("被动任务（document/read/audio）曾上报无脑返回成功虚标完成，改为 checkJobResponse() 校验；答题覆盖率 <80% 标 SKIPPED「仅保存未提交」。")
                        b("TTF 解析有 bbox 漏读、cmap4 偏移量、cmap12 对齐三处 Bug，均已修复。")
                        b("访问限制处理：不再伪装浏览器或模拟停留节奏；首个 403、429、验证码或限制页会触发账号/主机级熔断，禁止请求放大。")
                    },
                    unresolved {
                        b("悬浮窗需手动授权 SYSTEM_ALERT_WINDOW，拒绝后只有通知。")
                        b("首次绑定设备的微信验证码步骤未自动化。")
                    },
                    future {
                        p("作业提交功能有待研究接口后开放。")
                    },
                ),
            ),
            GuideEntry(
                id = "welearn",
                title = "WeLearn 随行课堂",
                summary = "外教社 welearn.sflep.com,刷完成度+真答案填空题",
                sections = listOf(
                    usage {
                        p("底部 Tab「WeLearn 随行课堂」，独立账号（与超星/智慧安大都无关）：用 welearn.sflep.com 的手机号 + 密码登录。")
                        p("登录后是课程列表，每张卡显示课程名 + 完成度进度条 + 百分比。点一门课进详情：顶部「完成度」卡（总进度 % + 已学习 X 时 Y 分），下方单元→章节树。单元默认全部展开，每节显示图标（已完成打勾、未开放显锁、未开始空心圆）+ 章节名 + 父路径 + 每节已学时长。")
                        p("三种刷课入口：")
                        b("详情页顶栏右上角的▶立刻启服务，刷全部 + 真答案填空题")
                        b("底部「选择性刷」按钮 → 弹单选 Dialog 选某一单元，立刻启服务只刷该单元")
                        b("底部「刷全部章节」 → 进 Study 屏，可调「正确率 / 增量全量 / 刷时长」参数后再按开始")
                        p("Study 屏：")
                        b("正确率输入框：默认 100，多课不同正确率可填「70,100」（按单元顺序使用对应值）")
                        b("增量 / 全量 切换：默认「增量」只刷未完成 sco，「全量」会把已完成的也重提交（覆盖分数）")
                        b("刷时长开关：默认开，每节 3 分钟（可改 1–60），期间每 30s 发一次心跳 keep，失败自动 retry 2 次")
                        b("大按钮「开始刷课 / 停止刷课」，下方进度卡：✓完成 / △部分 / ✗失败 / 跳过计数 + 进度条；心跳进行中再显示已用/总时 + ⚠keep 失败计数（被 carrier NAT 切断时这里会涨），底部日志区滚动显示关键步骤")
                    },
                    impl {
                        p("三屏式：WeLearnMainScreen（列表） → WeLearnCourseDetailScreen（单元-章节） → WeLearnStudyScreen（刷课控制台）；后台由 WeLearnStudyService ForegroundService 执行，通知 + 通知栏 / 悬浮窗显示实时进度。")
                        p("协议基础（SFLEP 账号登录态不可省）：")
                        b("WeLearnAuthRepository 复刻 OIDC 登录，Cookie 落 SessionManager.WELEARN_COOKIES_KEY")
                        b("课程列表：GET /ajax/authCourse.aspx?action=gmc（加 _t=nowMs cache-buster）")
                        b("单元：GET /student/course_info.aspx?cid=X 抠 uid+classid → GET /ajax/StudyStat.aspx?action=courseunits&cid=X&uid=Y")
                        b("章节：GET /ajax/StudyStat.aspx?action=scoLeaves&cid=X&uid=Y&unitidx=Z&classid=W → isvisible/iscomplete 映射 WeLearnScoStatus.LOCKED/COMPLETED/TODO")
                        b("课程统计：GET /ajax/StudyStat.aspx?action=scogeneral&cid=X&stuid=Y → Totalcount/Finishcount/ExAvgRate/Hour/Minute（H+M 折算 studiedSeconds 给详情卡显示）")
                        p("答题真答案数据：centercourseware.sflep.com 是 Aliyun OSS CDN，完全公开，无 auth/SMS/激活门。WeLearnAnswerRepository 走 POST /Ajax/SCO.aspx?action=scoAddr 拿 addr（形如 //centercourseware.sflep.com/课程名/index.html#/1/1-1-3|...）→ 解析 hash → GET https://...data/1/1-1-3.html → Jsoup 解 <et-blank> 填空 / <et-choice key='..'> 选择 → 构造 SCORM cmi interactions JSON。")
                        p("提交 POST /Ajax/SCO.aspx?action=setscoinfo：data 字段顶层必须 {「cmi」:{...}、「adl」:{...}、「cci」:{...}} 三键并列；漏掉 cmi 包装会让服务端 ret=0 但服务端不写状态（课程完成度不涨），这是最初踩到的最隐蔽的一个 bug。")
                        p("刷时长三步（每节独立 SCORM 会话）：startsco160928 开口 → keepsco_with_getticket_with_updatecmitime 每 30s keep（session_time + total_time 必填）→ savescoinfo160928 关，心跳模式 crate=0 避免覆盖完成度统计。")
                        p("session 过期嗅探：response body 以 < 开头或 JSON 解析失败 → 抛 IOException(「session expired: ...」)，VM 静默调 autoLoginIfPossible 用已存账密重登一次，外部 request 无感重试。")
                    },
                    problems {
                        b("cmi 包装 bug：cmiDataWithAnswers 把 SCORM 字段直接放 JSONObject 根，没用 {「cmi」:{...}、「adl」:{...}、「cci」:{...}} 三键顶层并列 → 提交 ret=0 但服务端不写状态，刷成功不涨进度。改完三键并列解决。")
                        b("心跳 PC 通手机不通：60s 节奏在手机端被 carrier NAT / 移动网络中间设备 RST 切断，首条 keep 就 readTimeout，后续全程失败，savescoinfo 关了一个空 session，learntime 0。改 30s 节奏 + keep 失败 retry 2 次（retry 前先 heartbeatStart 重启 session）+ UI 与通知栏暴露 keep 失败计数。")
                        b("服务端缓存返回旧 course.per / scoLeaves.iscomplete：列表/章节接口加 _t=System.currentTimeMillis() cache-buster 打破。")
                        b("session 过期：嗅探到 HTML 登录页（body 以 < 开头）→ 用存好的账密静默重登一次再重试，无需用户手动输入。")
                    },
                    unresolved {
                        b("录音题（<et-audio>）没有标准答案，目前跳过不提交，需要用户手动完成。")
                    },
                    future {
                        p("接 AI 续写开放题（复用 ChaoxingTikuRepository 的 AI provider，DeepSeek 优先）。")
                        p("离线缓存答案 CDN HTML 到本地，二次启动跳过 GET，加速首拉。")
                    },
                ),
            ),
            GuideEntry(
                id = "planned_platforms",
                title = "计划接入的第三方平台",
                summary = "正在调研的平台",
                sections = listOf(
                    future(heading = "U校园") {
                        p("做了初步调研，但是发现现有项目都有点老，不一定能实现。")
                    },
                    future(heading = "乐跑") {
                        p("研究了一点，但是涉及到一些定位模拟，还得再研究研究。")
                    },
                ),
            ),
        ),
    ),

    // ═══════════════════════ 系统与维护 ═══════════════════════
    GuideCategory(
        title = "系统与维护",
        entries = listOf(
            GuideEntry(
                id = "course_reminder",
                title = "课程提醒",
                summary = "上课前推送通知，在课表设置里配置",
                sections = listOf(
                    usage {
                        p("在课表的右上角设置→「课程提醒」分区：总开关开启，拖滑块选提前 1-30 分钟。开启时 App 会请求通知权限（Android 13+）；权限缺失时设置页会出现红色提示和「去开启」按钮。")
                    },
                    impl {
                        p("CourseReminderSettings Composable 自包含：直接读写 SessionManager + ActivityResultContracts.RequestPermission() 申请 POST_NOTIFICATIONS + ReminderPermissions.openExactAlarmSettings() 跳精确闹钟设置。CourseReminderScheduler.scheduleAll() 用 AlarmManager 按课程 id 注册，key 持久化到 DataStore，cancelAll() 时逐一精确取消避免配额耗尽（旧版 FLAG_UPDATE_CURRENT 覆盖模式已废弃）。Android 14+ 没有精确闹钟权限时自动降级 setAndAllowWhileIdle()，提醒可能误差几分钟。")
                    },
                ),
            ),
            GuideEntry(
                id = "widget",
                title = "桌面小组件",
                summary = "主屏小组件显示今日课程，每分钟自动刷新",
                sections = listOf(
                    usage {
                        p("在系统桌面长按→小组件→找到「安大 Plus」添加，显示今天要上的课。")
                    },
                    impl {
                        p("WidgetUpdateScheduler 活跃时段每 1 分钟自递归 AlarmManager 触发更新，开机后由 BootReceiver 恢复调度。进入 App 时也会触发一次强制刷新。")
                    },
                ),
            ),
            GuideEntry(
                id = "auto_update",
                title = "自动更新",
                summary = "启动时检查新版本，一键下载安装",
                sections = listOf(
                    usage {
                        p("每次打开 App 会悄悄检查有没有新版本。有的话弹窗展示更新内容，点「立即更新」自动下载好直接安装；也可点「暂不」跳过，下次打开还会提醒。更新弹窗比公告弹窗优先显示。")
                    },
                    impl {
                        p("UpdateManager 从 yao-enqi/ahu-plus-update Gitee 公开仓库拉 version.json，比较版本号；下载用 OkHttp 流式写文件，完成后 FileProvider 共享 URI 调系统安装器 Intent.ACTION_VIEW。")
                    },
                ),
            ),
            GuideEntry(
                id = "announcements",
                title = "开发者公告",
                summary = "启动弹窗，「我的→通知公告」看历史",
                sections = listOf(
                    usage {
                        p("有公告时打开 App 会弹出提示。看过的在「我的 → 通知公告」里翻历史。关掉的公告不会因退出登录重新弹出。")
                    },
                    impl {
                        p("AnnouncementRepository 与排考预测共用同一 Gitee 仓库，零登录拉取；已忽略公告 id 持久化到 DataStore（退登不清）。拉取失败静默跳过不影响启动。")
                    },
                ),
            ),
            GuideEntry(
                id = "guide",
                title = "使用帮助",
                summary = "就是你现在看的这个页面 😄",
                sections = listOf(
                    usage {
                        p("顶部「未来更新计划」汇总了所有已规划但还没做的功能，点进去能看详情，返回后还停在那一页。每个功能尽量从「怎么用」「怎么实现的」「踩过哪些坑」「尚未解决」「未来计划」几个角度说明，内容会随版本慢慢补全。")
                        p("搜索：顶部有搜索框，按标题 / 摘要 / 正文顺序匹配，忽略大小写，命中红色高亮 + 前后省略号截断。")
                        p("用户视角的 Q&A（数据安全、平台接入等）放在「我的 → 关于 → 常见问题」独立页面，跟本使用帮助是两份内容：这里是功能文档，那里是问答。")
                    },
                    future {
                        p("有疑问或建议可以在 GitHub Issues 反馈，会尽快回复。")
                    },
                ),
            ),
        ),
    ),
)
