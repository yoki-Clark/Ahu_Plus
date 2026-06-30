package com.yourname.ahu_plus.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 超星学习通全局请求头/Referer 注入拦截器。
 *
 * 解决问题:30 分钟 IP 封号(根因详见 commit message 中的调研报告)。
 *
 * 关键改动:
 * 1. 补全现代 Chrome 桌面端必发的 Client Hints 头(`sec-ch-ua*`),
 *    真实 Chrome 100+ 桌面端必带,缺失 = 直接被服务端 TLS 指纹 + 头部指纹交叉验证识别为非浏览器。
 * 2. 补全 `Accept-Language` / `X-Requested-With` / `Accept`,真实 XHR/导航必带。
 * 3. 按 URL 路径智能分发 `Referer`,模拟"从任务点 iframe 父页面跳过来"的真实浏览器行为:
 *    - 视频心跳 / 视频任务点 → `ananas/modules/video/index.html`
 *    - 音频任务点            → `ananas/modules/audio/index_new.html`
 *    - 阅读/文档任务点        → `ananas/modules/{read,document}/index.html`
 *    - 作业答题              → `mooc-ans/work/doHomeWorkNew`
 *    - 章节卡片              → `mooc2-ans/.../studentcourse`
 *    - 其余                  → `mooc1.chaoxing.com` 根域
 * 4. 跨域 POST(`/fanyalogin`、`/addStudentWorkNew`)加 `Origin`。
 *
 * 不在此处设 User-Agent:UA 由 Repository 显式传入(单值固定,见 `UA_FIXED`),
 * 这里只确保**没有**就补一个默认值,避免 0 头。
 *
 * 设计原则:此拦截器是**最末端的兜底**,调用方仍可显式覆盖。
 * (Real browsers never have empty headers; OkHttp 默认会注入 Host/User-Agent 等少量头,
 *  这层只在调用方没设时补齐,保证不会"裸请求"。)
 */
class ChaoxingHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url
        val urlStr = url.toString()
        val host = url.host
        val isPost = original.method == "POST"

        val builder = original.newBuilder()

        // 1. 现代 Chrome 桌面端 Client Hints(所有请求都补)
        if (original.header("sec-ch-ua").isNullOrEmpty()) {
            builder.header(
                "sec-ch-ua",
                "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""
            )
        }
        if (original.header("sec-ch-ua-mobile").isNullOrEmpty()) {
            builder.header("sec-ch-ua-mobile", "?0")
        }
        if (original.header("sec-ch-ua-platform").isNullOrEmpty()) {
            builder.header("sec-ch-ua-platform", "\"Windows\"")
        }

        // 2. Accept-Language(真实中文用户浏览器必带,服务端用此做地域风控聚类)
        if (original.header("Accept-Language").isNullOrEmpty()) {
            builder.header(
                "Accept-Language",
                "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6"
            )
        }

        // 3. Accept(JSON 端点需要 application/json,导航/资源需要 text/html)
        if (original.header("Accept").isNullOrEmpty()) {
            val accept = when {
                urlStr.contains("/api/") || urlStr.contains("json") ->
                    "application/json,text/plain,*/*"
                urlStr.contains("/ananas/") || urlStr.contains("/work/") ||
                    urlStr.contains("/knowledge/") || urlStr.contains("/mycourse/") ->
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
                else -> "*/*"
            }
            builder.header("Accept", accept)
        }

        // 4. Referer(按 URL 路径智能分发;无 Referer = 反作弊系统 anomaly 评分飙升)
        if (original.header("Referer").isNullOrEmpty()) {
            builder.header("Referer", pickReferer(urlStr, host))
        }

        // 5. X-Requested-With(所有非导航请求都标 XMLHttpRequest,与真实 SPA 一致)
        if (original.header("X-Requested-With").isNullOrEmpty() && !urlStr.contains("/page/")) {
            builder.header("X-Requested-With", "XMLHttpRequest")
        }

        // 6. Origin(仅跨域 POST 才带;GET/同域不设,真实浏览器行为)
        if (isPost && original.header("Origin").isNullOrEmpty() &&
            (urlStr.contains("passport2.chaoxing.com") ||
                urlStr.contains("mooc1.chaoxing.com") ||
                urlStr.contains("mooc-ans/work/"))
        ) {
            builder.header("Origin", "https://$host")
        }

        return chain.proceed(builder.build())
    }

    /**
     * 按 URL 路径选择最贴近的 Referer(真实浏览器从哪个父页面跳过来)。
     */
    private fun pickReferer(urlStr: String, host: String): String {
        return when {
            // 视频心跳 / 视频任务点 / 视频信息 → 视频 iframe 父页面
            urlStr.contains("/mooc-ans/multimedia/log/") ||
                urlStr.contains("/ananas/job/video") ||
                urlStr.contains("/ananas/status/") ->
                "https://mooc1.chaoxing.com/ananas/modules/video/index.html"

            // 音频任务点 → 音频 iframe 父页面
            urlStr.contains("/ananas/job/audio") ->
                "https://mooc1.chaoxing.com/ananas/modules/audio/index_new.html"

            // 阅读任务点 → 阅读 iframe 父页面
            urlStr.contains("/ananas/job/readv2") ->
                "https://mooc1.chaoxing.com/ananas/modules/read/index.html"

            // 文档任务点 → 文档 iframe 父页面
            urlStr.contains("/ananas/job/document") ->
                "https://mooc1.chaoxing.com/ananas/modules/document/index.html"

            // 直播回放
            urlStr.contains("/ananas/job/live") ->
                "https://mooc1.chaoxing.com/ananas/modules/live/index.html"

            // 章节卡片 → 课程主页
            urlStr.contains("/mooc-ans/knowledge/cards") ->
                "https://mooc2-ans.chaoxing.com/mycourse/studentcourse"

            // 作业答题
            urlStr.contains("/mooc-ans/work/") ->
                "https://mooc1.chaoxing.com/mooc-ans/work/doHomeWorkNew"

            // 课程列表
            urlStr.contains("/mooc2-ans/visit/courselistdata") ||
                urlStr.contains("/mooc2-ans/mycourse/") ->
                "https://mooc2-ans.chaoxing.com/mycourse/studentcourse"

            // 签到
            urlStr.contains("/pptSign/") || urlStr.contains("/v2/apis/active/") ||
                urlStr.contains("/sign/") ->
                "https://mobilelearn.chaoxing.com/v2/apis/active/student/activelist"

            // 登录页 → refer 到登录前的来源
            urlStr.contains("passport2.chaoxing.com") ->
                "https://passport2.chaoxing.com/fanyalogin"

            // 通知
            urlStr.contains("notice.chaoxing.com") ->
                "https://notice.chaoxing.com/pc/notice/listNotice"

            // 默认:当前 host 根
            else -> "https://$host/"
        }
    }
}
