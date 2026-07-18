package com.ahu_plus.ui.screen.profile

/**
 * 常见问题（FAQ）数据层。
 *
 * 与 [GuideContent] 的区别：
 *  - GuideContent 是「功能文档」（怎么用 / 怎么实现 / 已知坑 / 未来计划），按功能条目展开；
 *  - FaqContent 是「用户视角的 Q&A」，按主题分类整理，便于用户在「我的 → 常见问题」直接查阅。
 *
 * 结构两层：[FaqCategory] → [FaqItem]。
 *  - 一个「分类」对应一组相关问题（如「第三方平台接入」）。
 *  - 一个「条目」是一条问答（question + answer）。
 *
 * ─── 后续补问题只需改 [faqCategories] 这个 list ───
 */
data class FaqItem(
    val question: String,
    val answer: String,
)

data class FaqCategory(
    val title: String,
    val items: List<FaqItem>,
)

val faqCategories: List<FaqCategory> = listOf(
    FaqCategory(
        title = "登录与账号",
        items = listOf(
            FaqItem(
                question = "为什么打开 App 没有先出现登录页？",
                answer = "这是当前设计。App 恢复本地状态后直接进入主界面；保存过校园账号时，" +
                    "会在后台静默恢复会话。没有登录也可以查看天气、公告等公开内容，" +
                    "需要校园账号的页面会提供登录或重认证入口。"
            ),
            FaqItem(
                question = "为什么登录成功后某个功能仍提示登录？",
                answer = "App 连接多个独立系统。校园 CAS 只负责部分校园服务；教室课表、集市、" +
                    "学习通、WeLearn 和大学计算机平台都有自己的登录态。请在出错功能对应的页面登录或刷新。"
            ),
            FaqItem(
                question = "退出校园账号会删除什么？",
                answer = "会清除校园账号凭据、会话和账户缓存；当前实现还会清除教室课表、学习通、" +
                    "WeLearn 和大学计算机平台的账号会话。集市身份、普通设置和课程备注会保留。"
            ),
        ),
    ),
    FaqCategory(
        title = "数据与刷新",
        items = listOf(
            FaqItem(
                question = "为什么打开页面先看到旧数据？",
                answer = "课表、成绩、考试、学生信息、财务和考勤等页面采用本地优先策略：" +
                    "先展示缓存，再按刷新策略请求网络。需要最新结果时可以下拉刷新，并留意页面更新时间。"
            ),
            FaqItem(
                question = "为什么校园卡余额没有缓存值？",
                answer = "余额按实时数据处理。App 优先请求智慧安大余额，失败时回退到门户；" +
                    "两条链都失败会显示错误，不会把旧余额当作当前余额。"
            ),
            FaqItem(
                question = "为什么排考预测和正式考试安排不一样？",
                answer = "正式考试来自教务系统。排考预测使用公开托管的教室占用扫描结果，" +
                    "再按课程号与个人课表匹配，只能作为辅助信息。请以教务系统和学院通知为准。"
            ),
            FaqItem(
                question = "为什么教务通知或校长信箱先显示安全校验？",
                answer = "相关网站使用 WAF。App 会通过隐藏 WebView 完成站点的 JavaScript 校验，" +
                    "取得短期 Cookie 后再继续原生加载。校验失败时可以重试。"
            ),
        ),
    ),
    FaqCategory(
        title = "第三方服务",
        items = listOf(
            FaqItem(
                question = "为什么底栏没有集市、学习通或 WeLearn？",
                answer = "先在「我的 → 第三方服务」开启总开关和对应服务，再到「我的 → 设置」选择固定项。" +
                    "首页、应用、我的固定显示，三个第三方服务中最多固定两个；其余已启用服务仍可从应用中心进入。"
            ),
            FaqItem(
                question = "集市身份如何导入？",
                answer = "集市不使用校园 CAS。可以在集市设置中粘贴身份字段，或使用仓库里的 Windows " +
                    "辅助工具生成本地二维码后扫码导入。身份等同于登录态，不要发送给他人。"
            ),
            FaqItem(
                question = "集市提示身份失效怎么办？",
                answer = "请求返回 401、403 或身份过期时，App 会提示失效。重新从对应小程序获取并导入即可；" +
                    "同一学校的新身份会替换旧身份。"
            ),
            FaqItem(
                question = "学习通默认会访问外部题库吗？",
                answer = "不会。默认题库链只有本地 CACHE。言溪、GO、LIKE、自部署 Adapter、" +
                    "通用 AI 和 SiliconFlow 都需要用户主动配置。"
            ),
            FaqItem(
                question = "学习通视频支持多倍速或并发学习吗？",
                answer = "当前自动学习按课程、章节和任务串行执行，视频有效速度限制为 0.1x 到 1.0x。" +
                    "旧版本文档中的高倍速或并发描述不适用于当前代码。"
            ),
            FaqItem(
                question = "学习通签到可靠吗？",
                answer = "普通、手势和位置签到已有实现；签到码、二维码和拍照签到仍有协议校准边界，" +
                    "不能保证适配所有课程。结果应回到学习通官方页面确认。"
            ),
            FaqItem(
                question = "WeLearn 为什么有任务未完成？",
                answer = "自动学习只处理当前解析器和提交器支持的任务。录音、作文、未开放或无法确认语义的 " +
                    "SCO 可能被跳过，完成状态以 WeLearn 服务端刷新后的结果为准。"
            ),
        ),
    ),
    FaqCategory(
        title = "校园卡与生活服务",
        items = listOf(
            FaqItem(
                question = "支付码为什么加载失败？",
                answer = "支付码来自智慧安大服务。连续刷新、学校服务异常或会话过期都可能导致失败；" +
                    "该服务还有较严格的访问频率限制。等待一段时间后再试，避免反复点击。"
            ),
            FaqItem(
                question = "当前支持哪些充值？",
                answer = "浴室、空调、照明和网费页面已接入在线充值流程。一卡通主余额充值未接入，" +
                    "应使用学校官方渠道。充值会产生真实交易，提交前核对金额和账户。"
            ),
            FaqItem(
                question = "大学计算机平台换地址后为什么连不上？",
                answer = "该平台使用明文 HTTP，Android 只允许网络安全配置白名单中的地址。" +
                    "当前白名单是 172.17.106.232，其他 HTTP 地址可能被系统网络策略拒绝。"
            ),
        ),
    ),
    FaqCategory(
        title = "更新、安全与故障",
        items = listOf(
            FaqItem(
                question = "账号和身份凭据存在哪里？",
                answer = "账号、密码、Cookie、JWT 和 Bearer Token 使用 Android Keystore 支持的 " +
                    "EncryptedSharedPreferences。普通偏好和业务缓存使用 DataStore。" +
                    "如果设备 Keystore 不可用，敏感值只保留在当前进程，不会回退为明文持久化。"
            ),
            FaqItem(
                question = "自动更新会做哪些校验？",
                answer = "下载只接受 HTTPS，支持主地址失败后镜像回退，也可以取消。APK 下载后会检查包名、" +
                    "签名和发布清单中可选的 SHA-256；校验失败不会启动系统安装器。"
            ),
            FaqItem(
                question = "页面一直报网络错误怎么办？",
                answer = "先确认系统时间和网络正常，再判断是否只有一个系统失败。学校域名、公开托管源和第三方平台彼此独立。" +
                    "可以下拉刷新一次；遇到 403、429、验证码或访问限制时不要连续重试。"
            ),
            FaqItem(
                question = "App 闪退如何反馈？",
                answer = "记录版本号、Android 版本、机型、操作路径和发生时间。能稳定复现时，" +
                    "附上不含账号、Token、Cookie 和学生信息的日志；界面问题再附截图或录屏。"
            ),
        ),
    ),
)
