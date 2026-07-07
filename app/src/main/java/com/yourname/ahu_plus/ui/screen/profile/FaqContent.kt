package com.yourname.ahu_plus.ui.screen.profile

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
        title = "安全与隐私",
        items = listOf(
            FaqItem(
                question = "这个软件有数据安全隐患么？",
                answer = "总体安全。账号密码用 DataStore 加密保存在你手机本地，" +
                    "集市 token、学习通账密等用户手动配置也只在本地留存，" +
                    "而且退出登录以外的任何路径都不会被清空（包括认证失败时的轻量重认证）。" +
                    "需要联网的数据都是请求学校官方接口或公开 API（Gitee 仓库），中间不经过第三方代理。"
            ),
        ),
    ),
    FaqCategory(
        title = "第三方平台接入",
        items = listOf(
            FaqItem(
                question = "WeLearn 的刷题能保证正确率么？",
                answer = "可以的。WeLearn 的答案模块做的不好，可以直接在前端获取到答案，" +
                    "所以这些答案都是直接从前端扣出来填入的，理论上来说可以达到百分百的正确率。"
            ),
            FaqItem(
                question = "WeLearn 为什么跑完了，还是有一些任务点没完成？",
                answer = "那些是录音题或者作文题这种没法直接作答的题目。"
            ),
            FaqItem(
                question = "什么时候能接入 u 校园？",
                answer = "这个我还得研究研究。做过初步调研，但现有可参考的开源实现都比较老，" +
                    "不确定能不能稳定跑起来，会再评估一下。"
            ),
            FaqItem(
                question = "什么时候能接入步道乐跑？",
                answer = "这个有点难，估计一时半会有点难实现。" +
                    "步道乐跑涉及定位模拟，绕开检测的思路需要持续投入，所以优先级靠后。"
            ),
            FaqItem(
                question = "能接入 NFC 么？",
                answer = "支付和刷卡（闸机、考勤、打水）因硬件限制做不了。" +
                    "安大校园卡是 JCOP41，刷卡认的是卡片固定 UID，" +
                    "而手机 NFC 模拟给出的是随机 UID 且无法自定义，所以替代不了实体卡；" +
                    "支付那层还有金融密钥，更没法碰。"
            ),
        ),
    ),
    FaqCategory(
        title = "登录与账号",
        items = listOf(
            FaqItem(
                question = "可以支持手机验证码登录么？",
                answer = "是可以的，但是考虑到如果要维持登录态，使用账密还是最方便的。" +
                    "因为如果你的登录态过期了就会需要重新登录，而你又要重新手机验证码登录，" +
                    "会比较麻烦。所以目前还是推荐用账密登录。"
            ),
        ),
    ),
    FaqCategory(
        title = "功能实现",
        items = listOf(
            FaqItem(
                question = "学习通刷课有 bug 怎么还不修复？",
                answer = "这块不是我开发的，我是用的 GitHub 上现成的项目（Samueli924/chaoxing 移植），" +
                    "所以自身对这个实现逻辑掌握的不好，修起来会有点困难。" +
                    "能修的尽量在修（比如之前修复了被动任务假成功、TTF 解析三连 bug），" +
                    "实在拿不准的会先在公告里告知再动手。"
            ),
            FaqItem(
                question = "用学习通刷课会被封么？",
                answer = "有可能，已经有用户反馈被封。超星反作弊会交叉验证 UA/IP、请求头指纹、进度上报节奏和任务点停留时间，" +
                    "所以已做一次加固：UA 固定为单值、补全 sec-ch-ua / Accept-Language / Referer / Origin 等请求头、" +
                    "视频心跳从 30~90s 改为 5~10s、任务点上报后延迟 15~90s 模拟真人停留。" +
                    "但即便如此也不能保证完全不被封，不同课程可能还有额外检测。一般封禁后半个小时到一天会恢复（多数半小时），提示多为「IP 异常」。" +
                    "如果你不太放心，建议不刷，或用网页端成熟插件/脚本。"
            ),
            FaqItem(
                question = "为什么支付码有时候加载不出来？",
                answer = "这个支付码本身用的还是学校的智慧安大接口（adwmh.ahu.edu.cn），" +
                    "所以智慧安大崩了，这个也就自然加载不出来了。" +
                    "另外智慧安大只接受 TLS 1.2，且有约 2-3 次/分钟的速率限制，" +
                    "短时间内连续刷新也可能失败，等一会儿重试即可。"
            ),
            FaqItem(
                question = "消费账单显示太卡什么情况？",
                answer = "第一次初次加载会加载全量数据，可能确实会比较久，" +
                    "后面再点开就会好很多了。" +
                    "账单结果会缓存到本地，所以只要不是切换账号或清缓存，体验是逐次变好的。"
            ),
            FaqItem(
                question = "这个里面的天气为什么和我手机软件的天气不一样？",
                answer = "因为接口用的是公开的天气 API，可能和你手机自带天气的数据来源不是一个。"
            ),
        ),
    ),
    FaqCategory(
        title = "未来发展",
        items = listOf(
            FaqItem(
                question = "未来会持续开发么？",
                answer = "应该会。即使不继续开发，只要学校不换接口，这个软件还是能继续用的。" +
                    "代码、数据缓存逻辑都摆在 GitHub 上，欢迎其他人接手维护。"
            ),
            FaqItem(
                question = "什么时候加题库？",
                answer = "题库功能需要搭服务器以存储相应的数据，会比较费钱，" +
                    "我本身不是很富裕。其次是我并没有很多有关这些的资料。"
            ),
            FaqItem(
                question = "现在支持充值功能吗？",
                answer = "已支持校园卡页面的浴室、空调、照明、网费在线充值。充值时输入金额和 6 位查询密码（默认从学生信息自动填充），" +
                    "走 blade-pay/pay 下单→拉码→提交三步流程。一卡通主余额充值暂未接入，仍需要去线下或学校官方渠道。"
            ),
        ),
    ),
)
