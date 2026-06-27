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
                question = "什么时候能接入 WeLearn？",
                answer = "快了。接口已经初步调研过，难度不大，能实现自动刷课，" +
                    "等学习通这边稳定后再排期。"
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
                answer = "在计划中。安大校园卡是 JCOP41 Java Card，" +
                    "支付功能估计不太行（涉及金融密钥），应该只能提供打卡/签到的功能，" +
                    "不过这块我也不太会，能不能做出来还不好说。"
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
                answer = "有可能，已经有用户反馈被封了。不过我并不清楚具体是什么原因导致的封，" +
                    "有人反馈说可能是课程有一些特殊设置，导致不能刷，具体的由于反馈不太完整，" +
                    "我不太确定。一般封了之后半个小时到一天会恢复（绝大多数是半小时），" +
                    "封的时候一般提示「IP 异常」。" +
                    "我个人推测可能是开了倍速或并发导致请求过高被封？但没有复现出来，不是很确定。" +
                    "如果你不太放心，可以不刷，用网页端的那些成熟插件或脚本。"
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
                question = "啥时候可以支持充值功能？",
                answer = "这部分涉及实际金钱，我需要再多做一点测试，" +
                    "以确保没有问题之后再进行内测，后续再添加进去。"
            ),
        ),
    ),
)
