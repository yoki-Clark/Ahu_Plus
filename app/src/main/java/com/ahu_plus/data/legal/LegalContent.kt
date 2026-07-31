package com.ahu_plus.data.legal

enum class LegalDocumentKind(val title: String) {
    PRIVACY_POLICY("隐私政策"),
    DISCLAIMER("免责声明与使用须知"),
    THIRD_PARTY_SERVICES("第三方服务清单"),
    PERMISSIONS("权限使用说明"),
}

data class LegalSection(
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
)

data class LegalDocument(
    val kind: LegalDocumentKind,
    val version: Int,
    val effectiveDate: String,
    val introduction: String,
    val sections: List<LegalSection>,
)

object LegalContent {
    const val CONTACT_EMAIL = "2867299793@qq.com"

    fun document(kind: LegalDocumentKind): LegalDocument = when (kind) {
        LegalDocumentKind.PRIVACY_POLICY -> privacyPolicy
        LegalDocumentKind.DISCLAIMER -> disclaimer
        LegalDocumentKind.THIRD_PARTY_SERVICES -> thirdPartyServices
        LegalDocumentKind.PERMISSIONS -> permissions
    }

    private val privacyPolicy = LegalDocument(
        kind = LegalDocumentKind.PRIVACY_POLICY,
        version = LegalDocumentVersions.PRIVACY,
        effectiveDate = LegalDocumentVersions.EFFECTIVE_DATE,
        introduction = "安大加重视个人信息与数据安全。本政策说明应用在提供校园助手功能时如何处理信息。请在使用前完整阅读；相机、位置、日历、第三方服务等非基础能力仍会在具体场景中另行征求授权。",
        sections = listOf(
            LegalSection(
                title = "一、运营者与联系渠道",
                paragraphs = listOf(
                    "安大加由安大加项目个人开发者运营，是面向安徽大学校园场景的非官方客户端。隐私问题、数据清理或投诉建议可发送邮件至 $CONTACT_EMAIL。",
                ),
            ),
            LegalSection(
                title = "二、处理的信息与用途",
                bullets = listOf(
                    "校园认证信息：学号、密码及学校系统签发的 Cookie、Session、JWT，用于登录并维持用户主动使用的校园服务会话。",
                    "校园业务信息：课表、成绩、考试、培养方案、考勤、学生信息、住宿、财务、校园卡余额和消费记录，用于对应页面展示、缓存和提醒。",
                    "第三方账号信息：集市身份、学习通、WeLearn、教务移动端及大学计算机平台的账号或会话，仅用于用户明确启用的对应服务。",
                    "用户创建的信息：课程备注、考核方案、作业、日程、房间或手机号配置、应用设置，用于在设备上提供个性化功能。",
                    "运行与诊断信息：请求结果分类、缓存时间、脱敏错误和设备权限状态，用于故障提示与用户主动打开的开发者诊断。应用当前不集成广告、行为统计或崩溃上报 SDK。",
                ),
            ),
            LegalSection(
                title = "三、存储与安全",
                bullets = listOf(
                    "账号、密码、Cookie、JWT、集市身份和 API Key 使用 Android Keystore 支持的加密存储；加密存储不可用时不会回退到明文持久化。",
                    "普通设置和业务缓存保存在应用私有 DataStore 中；应用已禁用 Android 系统备份。",
                    "网络日志会避免记录 Authorization、Cookie、验证码、支付码原文和完整私有响应。",
                    "本地信息通常保留至用户退出对应账号、执行清理、清除应用数据或卸载应用。导出到相册的图片和同步到系统日历的事件需由用户在对应系统中另行删除。",
                ),
            ),
            LegalSection(
                title = "四、信息传输与第三方服务",
                paragraphs = listOf(
                    "应用主要直接连接安徽大学及用户启用的第三方平台。服务器通常会接收网络通信必需的 IP 地址、User-Agent、请求时间等信息。具体接收方、目的和数据类型见《第三方服务清单》。",
                    "智慧安大验证码图片仅从 adwmh.ahu.edu.cn 获取并展示给用户手动输入，不发送至任何 OCR 网站或识别服务。用户配置外部 AI、题库或通知地址前，应用会展示目标服务信息；相关内容只在用户主动使用时发送。",
                ),
            ),
            LegalSection(
                title = "五、设备权限",
                paragraphs = listOf(
                    "通知、精确闹钟、日历、相机、位置、悬浮窗、旧版存储和安装更新权限均按功能申请。拒绝非必要权限不会影响不依赖该权限的其他功能。详细用途见《权限使用说明》。",
                ),
            ),
            LegalSection(
                title = "六、用户权利",
                bullets = listOf(
                    "可在系统设置中撤回相机、位置、通知、日历和悬浮窗等权限。",
                    "可退出单个账号、清理缓存，或在“关于 → 个人数据管理”撤回隐私同意、清除全部应用内数据。",
                    "撤回同意后应用停止新的联网、静默认证和后台调度；再次使用联网功能前需要重新同意。",
                    "应用不能代替学校或第三方平台删除其服务器保存的数据，相关权利需向对应平台提出。",
                ),
            ),
            LegalSection(
                title = "七、未成年人",
                paragraphs = listOf(
                    "本应用主要面向高校用户，不以不满十四周岁的未成年人为目标用户。不满十四周岁的用户应在监护人同意和指导下使用。",
                ),
            ),
            LegalSection(
                title = "八、政策更新",
                paragraphs = listOf(
                    "当处理目的、敏感信息范围、对外提供对象或权限用途发生重大变化时，应用会提高政策版本并在继续联网前重新征求同意。文字修正或联系方式更新会在文档中标注。",
                ),
            ),
        ),
    )

    private val disclaimer = LegalDocument(
        kind = LegalDocumentKind.DISCLAIMER,
        version = LegalDocumentVersions.DISCLAIMER,
        effectiveDate = LegalDocumentVersions.EFFECTIVE_DATE,
        introduction = "本说明用于明确软件能力边界和使用风险，不排除法律规定不得免除的责任，也不影响用户依法享有的权利。",
        sections = listOf(
            LegalSection(
                title = "一、非官方性质",
                paragraphs = listOf(
                    "安大加是独立开发的非官方客户端，不代表安徽大学、校园集市、学习通、WeLearn 或其他接入平台。学校和平台的官方页面、记录、通知与规则具有优先效力。",
                ),
            ),
            LegalSection(
                title = "二、数据与服务可用性",
                paragraphs = listOf(
                    "页面内容可能来自本地缓存，学校系统和第三方接口也可能变更、限流、维护或返回异常。课表、成绩、考试、考勤、余额、账单和通知等重要信息应在官方渠道复核。",
                    "提醒、Widget、日历同步受系统权限、省电策略、设备时间和后台限制影响，不保证在所有设备上准时送达。",
                ),
            ),
            LegalSection(
                title = "三、账号与平台规则",
                paragraphs = listOf(
                    "用户只能使用本人有权使用的账号和数据，并应遵守学校及第三方平台规则。自动学习、答题、签到或批量操作可能受到平台限制，使用前应确认课程要求和平台规则；应用展示的执行结果不替代平台最终状态。",
                ),
            ),
            LegalSection(
                title = "四、支付与真实操作",
                paragraphs = listOf(
                    "充值、支付码、校长信箱提交、评价等操作会产生真实外部状态。提交前应核对账户、对象、金额和内容，交易结果以学校或支付渠道记录为准。不要向他人展示有效支付码或登录身份。",
                ),
            ),
            LegalSection(
                title = "五、外部 AI、题库与自定义服务",
                paragraphs = listOf(
                    "外部 AI、题库、通知地址和自定义服务由用户主动配置，其内容准确性、费用、稳定性、隐私规则和知识产权责任由对应服务及用户行为决定。AI 或题库输出仅供参考，不应直接用于重要决策。",
                ),
            ),
            LegalSection(
                title = "六、责任边界",
                paragraphs = listOf(
                    "在法律允许范围内，开发者不对学校或第三方服务中断、用户错误操作、设备环境或不可抗力造成的间接损失作无条件保证。本条不免除开发者因故意、重大过失、安全缺陷、违法处理个人信息或其他依法不得免除事项应承担的责任。",
                ),
            ),
        ),
    )

    private val thirdPartyServices = LegalDocument(
        kind = LegalDocumentKind.THIRD_PARTY_SERVICES,
        version = 1,
        effectiveDate = LegalDocumentVersions.EFFECTIVE_DATE,
        introduction = "以下清单按当前代码整理。实际请求只会在启动检查、页面加载、登录或用户启用对应功能时发生。",
        sections = listOf(
            LegalSection("安徽大学校园系统", bullets = listOf(
                "one.ahu.edu.cn：CAS 登录、学生信息及门户服务；可能处理账号、会话和校园业务数据。",
                "jw.ahu.edu.cn、jwapp.ahu.edu.cn：教务、课表、成绩、考试、培养方案等；可能处理账号、会话和教务数据。",
                "ycard.ahu.edu.cn、adwmh.ahu.edu.cn、kqcard.ahu.edu.cn：一卡通、支付码、余额、账单和考勤；可能处理会话及对应业务数据。智慧安大验证码仅供手动输入。",
                "wvpn.ahu.edu.cn、用户配置的校内地址：大学计算机平台或校内服务访问；可能处理独立账号、验证码和业务数据。",
                "www6.ahu.edu.cn 等学校公开站点：通知和校长信箱；写信时处理用户主动填写的联系人、联系方式和正文。",
            )),
            LegalSection("用户启用的第三方平台", bullets = listOf(
                "api.zxs-bbs.cn：校园集市内容和身份认证，处理导入的 Bearer 身份及用户请求的帖子、评论和消息。",
                "*.chaoxing.com：学习通登录、课程、作业、签到和学习任务，处理学习通账号、会话、任务及用户主动提交的数据。",
                "*.sflep.com：WeLearn 登录、课程和学习任务，处理独立账号、会话和任务数据。",
                "api.deepseek.com：集市 AI 评论助手；仅在用户确认启用并主动生成时接收帖子、评论上下文和提示词。",
                "tk.enncy.cn、q.icodef.com、www.datam.site：可选学习通题库；仅在用户选择对应来源后接收待查询的题目和选项。",
                "用户配置的 OpenAI 兼容模型、题库适配器、通知或自部署地址：只在用户启用并发起对应操作时发送界面已说明的内容。",
            )),
            LegalSection("公共内容与更新服务", bullets = listOf(
                "gitee.com、raw.githubusercontent.com：获取版本清单、APK 下载地址和开发者公告。",
                "api.open-meteo.com、air-quality-api.open-meteo.com：获取固定合肥区域的天气和空气质量，不上传设备精确位置。",
            )),
            LegalSection("系统能力", bullets = listOf(
                "Android 系统日历：仅在用户授权并主动同步时写入课程、考试或日程。",
                "系统相册或文档选择器：仅在用户主动选择背景、附件或导出图片时访问指定内容。",
            )),
        ),
    )

    private val permissions = LegalDocument(
        kind = LegalDocumentKind.PERMISSIONS,
        version = 1,
        effectiveDate = LegalDocumentVersions.EFFECTIVE_DATE,
        introduction = "应用不会在首次启动时批量申请权限。运行时权限会在用户进入对应功能后申请。",
        sections = listOf(
            LegalSection("网络与后台", bullets = listOf(
                "网络：连接学校系统、用户启用的第三方服务、天气、公告和更新源。",
                "开机启动、唤醒和精确闹钟：重排课程及日程提醒、维护桌面 Widget；可通过关闭提醒或撤回隐私同意停止。",
                "前台服务：用户主动开始学习通或 WeLearn 后台任务时维持任务并显示通知。",
            )),
            LegalSection("通知与日历", bullets = listOf(
                "通知：显示课程、日程、余额告警和后台任务状态。拒绝后仅影响通知展示。",
                "读取/写入日历：用户主动同步日程时查询和写入系统日历。拒绝后仍可在应用内查看日程。",
            )),
            LegalSection("相机、位置与文件", bullets = listOf(
                "相机：扫描集市身份二维码，或完成用户主动发起的学习通拍照/扫码签到。",
                "精确或大致位置：仅在用户主动进行学习通位置签到时读取并提交给学习通。",
                "旧版存储权限：Android 9 及以下保存课表或帖子图片；新系统使用媒体和文档选择器。",
            )),
            LegalSection("特殊权限", bullets = listOf(
                "悬浮窗：用户启用后显示后台学习进度；默认关闭。",
                "安装未知应用：用户确认下载更新后交由系统安装界面处理；应用只接受 HTTPS 下载地址并校验签名。",
            )),
        ),
    )
}
