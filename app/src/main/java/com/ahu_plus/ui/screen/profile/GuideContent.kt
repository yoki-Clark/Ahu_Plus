package com.ahu_plus.ui.screen.profile

/**
 * 使用帮助文档的数据层。用户视角的问答由 [faqCategories] 单独维护。
 *
 * 结构三层：[GuideCategory] → [GuideEntry] → [GuideSection]。
 *  - 一个「分类」对应文档里的一级标题（如 `# 基础功能`）
 *  - 一个「条目」对应二级标题（如 `### 登录与会话存留`）
 *  - 一个「小节」对应三级标题（如 `##### 使用方式`）
 *
 * 五种固定小节（[GuideSectionKind]）：使用方式 / 实现方式 / 出现的问题与解决 /
 * 尚未解决的问题 / 未来的计划。**不要求每个条目都填满**——内容为空的小节不会渲染。
 *
 * 数据结构仍支持自动聚合 [GuideSectionKind.FUTURE] 小节（见 [roadmapItems]），
 * 但当前版本未发布路线图入口，也不在应用内承诺未实现功能。
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
 *   仅在一个条目里需要并列多个同类小节时使用。
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

/** 未来计划小节；当前帮助正文不使用。 */
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
//  应用内帮助正文。内容只描述当前代码已实现的行为；规划项不在这里承诺。
// ──────────────────────────────────────────────────────────────────────

val guideCategories: List<GuideCategory> = listOf(
    GuideCategory(
        title = "基础功能",
        entries = listOf(
            GuideEntry(
                id = "login",
                title = "启动、登录与会话",
                summary = "先进入主界面，需要时再登录，已保存账号会在后台恢复会话",
                sections = listOf(
                    usage {
                        p("App 启动后直接进入主界面，不等待网络认证。没有校园账号时，天气、公告等公开功能仍可使用；需要校园数据的页面会提供登录或重认证入口。")
                        p("校园账号使用学号和智慧安大密码。保存过账号后，App 会在后台恢复 CAS、一卡通和智慧安大会话，失败不会遮挡主界面。")
                        b("教室课表使用独立的教务微服务账号。")
                        b("集市使用从小程序获取的身份字段。")
                        b("学习通、WeLearn 和大学计算机平台各自使用独立账号或会话。")
                    },
                    impl {
                        p("校园登录由 CasAuthRepository 完成 CAS 认证，再为各校园系统建立会话。账号、密码、Cookie、JWT 和集市身份写入 Android Keystore 支持的 EncryptedSharedPreferences；普通偏好和业务缓存写入 DataStore。")
                        p("显式退出校园账号会清除校园账号、账户缓存，以及教室课表、学习通、WeLearn 和大学计算机平台会话；集市身份、普通设置和课程备注保留。")
                    },
                ),
            ),
            GuideEntry(
                id = "dashboard",
                title = "首页工作台",
                summary = "今日课程、日程、最近使用、教务通知和天气",
                sections = listOf(
                    usage {
                        p("首页聚合今日课程、近期日程、最近使用的 3 个应用、教务通知预览和天气。点击课程进入课表，点击「更多」进入应用中心。")
                        p("最近使用会按实际打开记录更新；没有记录时会用课表、成绩和考试补足默认入口。")
                    },
                    impl {
                        p("今日课程来自课表并按当前日期、周次和节次筛选；日程合并课程、考试、作业和自定义事件。教务通知需要时会通过隐藏 WebView 完成站点 WAF 校验。")
                    },
                ),
            ),
            GuideEntry(
                id = "apphub",
                title = "应用中心与顶层导航",
                summary = "所有功能的总入口，可搜索并固定第三方服务",
                sections = listOf(
                    usage {
                        p("应用中心按学习、通知、校园卡、生活和个人信息分组，也支持按标题和分组搜索。已启用的集市、学习通和 WeLearn 会显示为第三方服务入口。")
                        p("首页、应用、我的始终显示。在「我的 → 设置」中，可以从集市、学习通、WeLearn 中最多固定 2 个到底部导航；未固定但已启用的服务仍可从应用中心进入。")
                    },
                ),
            ),
        ),
    ),
    GuideCategory(
        title = "教务学业",
        entries = listOf(
            GuideEntry(
                id = "schedule",
                title = "课表、日程与作业",
                summary = "查看学期课表，维护备注、考核方案、作业和自定义事件",
                sections = listOf(
                    usage {
                        p("课表支持切换学期和周次、周视图、课程详情和下拉刷新。设置页可调整列宽、行高、字体、周末列、分页方式和进入页面时的周次行为。")
                        p("课程详情包含课程信息、考核方案、课程备注、节次备注、记录和作业。自定义课程、备注、作业和自定义日程只保存在本机，不会回写学校教务系统。")
                    },
                    impl {
                        p("课表优先展示本地缓存，再在后台通过教务会话刷新。课程提醒和自定义事件由独立的本地仓库管理。")
                    },
                ),
            ),
            GuideEntry(
                id = "grade",
                title = "成绩",
                summary = "按学期查看成绩、GPA 元数据和过程分项",
                sections = listOf(
                    usage {
                        p("成绩页按学期展示课程成绩和教务接口返回的 GPA 元数据。课程详情中的过程分项只显示服务端实际返回的内容。")
                    },
                    impl {
                        p("页面先读本地缓存，再通过 GradeRepository 刷新；不会根据总评反推或猜测未返回的平时成绩。")
                    },
                ),
            ),
            GuideEntry(
                id = "exam",
                title = "考试安排",
                summary = "查看正式考试安排，并可写入系统日历",
                sections = listOf(
                    usage {
                        p("考试页展示教务系统返回的课程、时间、考场和座位等信息，已结束考试会弱化显示。页面提供写入系统日历和进入排考预测的入口。")
                    },
                    impl {
                        p("正式安排来自 ExamRepository。写入系统日历需要日历权限，写入前会由系统日历界面再次确认。")
                    },
                ),
            ),
            GuideEntry(
                id = "exam_prediction",
                title = "排考预测",
                summary = "用公开扫描结果辅助匹配个人课程，不代替正式通知",
                sections = listOf(
                    usage {
                        p("排考预测按课程号把公开托管的教室占用扫描结果与本地课表匹配，并优先展示教师名能够对应的场次。网络失败时可能展示本地缓存。")
                    },
                    unresolved {
                        p("预测数据不是教务系统的正式考试安排，应以教务系统和学院通知为准。")
                    },
                ),
            ),
            GuideEntry(
                id = "empty_classroom",
                title = "空教室与教室课表",
                summary = "查询空闲教室，或用独立教务微服务查看教室占用",
                sections = listOf(
                    usage {
                        p("空教室支持校区、教学楼、楼层和日期筛选，并用节次时间轴显示空闲区间；「连续空闲」模式按可连续使用的节数排序。")
                        p("教室课表是另一个入口，使用独立账号登录教务微服务，可按学期、校区、教学楼、楼层、类型和座位数筛选后查看指定周次占用。")
                    },
                ),
            ),
            GuideEntry(
                id = "training_plan",
                title = "培养方案与完成进度",
                summary = "查看培养方案结构和课程完成状态",
                sections = listOf(
                    usage {
                        p("培养方案按模块展示课程要求；完成进度会结合教务返回的课程状态和学分统计，区分已通过、进行中、未通过和未选。")
                    },
                ),
            ),
        ),
    ),
    GuideCategory(
        title = "校园生活",
        entries = listOf(
            GuideEntry(
                id = "card",
                title = "校园卡与支付码",
                summary = "实时余额、消费账单、消费分析和智慧安大支付码",
                sections = listOf(
                    usage {
                        p("校园卡区域提供实时余额、消费账单、消费分析和智慧安大支付码。支付码可放大全屏，并可按设置临时调高屏幕亮度。")
                        p("余额优先请求智慧安大数据，失败时回退到门户；两条链都失败会显示错误，不会把旧余额冒充当前余额。账单和分析结果带本地缓存。")
                    },
                    unresolved {
                        p("支付码服务有访问频率限制，连续刷新可能失败。等待一段时间后再试，避免反复点击。")
                    },
                ),
            ),
            GuideEntry(
                id = "card_analytics",
                title = "消费账单与分析",
                summary = "按月或学期查看趋势、分类、商户和餐次统计",
                sections = listOf(
                    usage {
                        p("消费账单展示一卡通交易记录；消费分析支持按月和按学期汇总总额、日均、趋势、分类、商户排行和餐次分布。")
                    },
                    impl {
                        p("统计由客户端基于已获取的账单记录计算，不会修改一卡通数据。首次拉取全量账单可能较慢，后续优先使用缓存。")
                    },
                ),
            ),
            GuideEntry(
                id = "utilities",
                title = "浴室、空调、照明与网费",
                summary = "查看余额和明细，并使用当前已接入的在线充值流程",
                sections = listOf(
                    usage {
                        p("应用中心的生活分组提供浴室、空调、照明和网费页面，可查看对应余额、明细和当前代码已接入的在线充值入口。")
                        p("充值会产生真实交易。提交前核对账户、房间或手机号、充值项目和金额；一卡通主余额充值未接入，应使用学校官方渠道。")
                    },
                ),
            ),
            GuideEntry(
                id = "attendance",
                title = "考勤记录",
                summary = "查看校园考勤系统返回的课程、时间、教室和状态",
                sections = listOf(
                    usage {
                        p("考勤页按记录类型区分显示学校考勤系统返回的课程、时间、教室和状态。页面先展示缓存，再按刷新策略请求最新数据。")
                    },
                ),
            ),
            GuideEntry(
                id = "weather",
                title = "天气",
                summary = "Open-Meteo 公开天气和空气质量数据，位置固定合肥蜀山区",
                sections = listOf(
                    usage {
                        p("天气页显示当前天气、空气质量、逐小时预报和未来几日预报。数据来自 Open-Meteo，当前位置固定为合肥蜀山区。")
                    },
                    unresolved {
                        p("数据源和模型可能与手机厂商天气不同，当前版本不支持切换城市。")
                    },
                ),
            ),
            GuideEntry(
                id = "student_info",
                title = "学生信息、财务与学籍",
                summary = "基本信息、住宿、学业预警和财务汇总",
                sections = listOf(
                    usage {
                        p("学生信息分为基本信息、住宿数据和学业预警。财务汇总聚合奖学金、助学金、临时困难补助、勤工助学、欠费和贷款。")
                        p("这些页面采用本地优先策略，缓存用于快速展示，最终内容以学校服务端刷新结果为准。")
                    },
                ),
            ),
        ),
    ),
    GuideCategory(
        title = "第三方服务",
        entries = listOf(
            GuideEntry(
                id = "market",
                title = "校园集市",
                summary = "导入独立身份后看帖、发帖、评论、通知和管理多校身份",
                sections = listOf(
                    usage {
                        p("先在「我的 → 第三方服务」开启集市，再在集市设置中粘贴身份字段；也可以扫描 Windows 辅助工具生成的本地二维码导入。")
                        p("当前支持帖子列表、热榜、搜索、详情、评论与回复、发帖、通知、多图预览、单列或双列布局和多校身份。身份失效后需要重新获取。")
                    },
                    impl {
                        p("集市使用独立 Bearer 身份，不经过安大 CAS。身份使用加密存储，并在请求返回 401、403 或过期元数据时提示失效。")
                    },
                    unresolved {
                        p("集市身份等同于登录态，不要分享。App 无法代替小程序完成身份签发。")
                    },
                ),
            ),
            GuideEntry(
                id = "chaoxing",
                title = "学习通",
                summary = "独立账号登录，提供课程、作业、消息、签到、题库设置和自动学习",
                sections = listOf(
                    usage {
                        p("先启用学习通服务，再用学习通手机号和密码登录。可以浏览课程与章节、作业和消息，也可以使用签到、题库设置和自动学习。")
                        p("自动学习按课程、章节和任务串行执行。视频从服务端记录位置继续，有效速度限制为 0.1x 到 1.0x。")
                        p("默认题库链只有本地缓存 CACHE；言溪、GO、LIKE、自部署 Adapter、通用 AI 和 SiliconFlow 都需要手动配置。")
                    },
                    unresolved {
                        p("签到码、二维码和拍照签到仍有协议校准边界。签到、作业和学习结果都应回到学习通官方页面确认。")
                    },
                ),
            ),
            GuideEntry(
                id = "welearn",
                title = "WeLearn 随行课堂",
                summary = "独立账号登录，浏览课程并执行当前解析器支持的学习任务",
                sections = listOf(
                    usage {
                        p("先启用 WeLearn 服务，再用 WeLearn 独立账号登录。页面可浏览课程、单元和 SCO，并执行当前解析器和提交器支持的自动学习与答题流程。")
                    },
                    unresolved {
                        p("录音、作文、未开放任务或无法确认语义的任务可能被跳过。只有服务端刷新后的状态才是最终结果。")
                    },
                ),
            ),
        ),
    ),
    GuideCategory(
        title = "系统与维护",
        entries = listOf(
            GuideEntry(
                id = "course_reminder",
                title = "课程提醒",
                summary = "在课表设置中配置上课前通知",
                sections = listOf(
                    usage {
                        p("在课表设置中开启课程提醒并选择提前时间。按 Android 版本授予通知和精确闹钟权限；没有精确闹钟权限时，提醒可能有数分钟误差。")
                    },
                    impl {
                        p("提醒由 AlarmManager 调度。设备重启或应用升级后会恢复调度，关闭提醒时会取消对应任务。")
                    },
                ),
            ),
            GuideEntry(
                id = "widget",
                title = "桌面小组件",
                summary = "在系统桌面显示今日课程",
                sections = listOf(
                    usage {
                        p("在系统桌面的小组件选择器中添加「安大 Plus」，即可查看今日课程。进入 App、定时任务和系统广播会触发刷新。")
                    },
                    impl {
                        p("BootReceiver 会在设备启动和应用更新后恢复小组件与课程提醒调度。")
                    },
                ),
            ),
            GuideEntry(
                id = "auto_update",
                title = "应用更新",
                summary = "检查所选渠道，下载、校验并交给系统安装",
                sections = listOf(
                    usage {
                        p("App 启动时检查所选更新渠道，也可以在「我的 → 关于」手动检查。下载可取消；主地址失败时会尝试镜像。")
                        p("Android 需要允许此应用安装未知来源 APK，才能进入系统安装流程。")
                    },
                    impl {
                        p("下载完成后会检查 APK 包名、签名和发布清单中可选的 SHA-256，再通过 FileProvider 交给系统安装器。校验失败不会启动安装。")
                    },
                ),
            ),
            GuideEntry(
                id = "announcements",
                title = "开发者公告",
                summary = "启动时检查公告，也可在关于页查看历史",
                sections = listOf(
                    usage {
                        p("有可展示公告时，App 会在启动后弹出提示；历史内容可在「我的 → 关于 → 通知公告」查看。公告拉取失败不会阻塞应用启动。")
                    },
                ),
            ),
            GuideEntry(
                id = "guide",
                title = "使用帮助与常见问题",
                summary = "功能说明和用户问答的入口",
                sections = listOf(
                    usage {
                        p("本页面按功能说明当前用法和已知边界，顶部搜索框会匹配标题、摘要和正文。")
                        p("「我的 → 关于 → 常见问题」按登录、数据、第三方服务、校园卡、更新和安全等主题提供问答。顶部「未来更新计划」入口当前关闭，不作为功能承诺。")
                    },
                ),
            ),
        ),
    ),
)
