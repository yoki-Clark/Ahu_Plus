# CODEMAP — 文件级地图

> **用途**：定位「改哪个文件」的索引。需要找某功能落点时读这里，**不要**为了定位去全局搜索或读 `CLAUDE.md`（那个讲架构，这个讲文件）。
> **维护**：新增/移动文件时更新对应行。功能描述以**代码为准**，本表过时即修。
> 按「目标模块」分组——这同时也是多模块拆分（task 3）的目标架构。

## ⚠️ 体积热点（改动前别整文件加载，优先定位到函数/区段）

| 文件 | 行数 | 备注 |
|------|-----:|------|
| `data/local/SessionManager.kt` | 3190 | 139 个 key，扁平无分区。**几乎所有 Repository 都注入它** → 头号上下文黑洞，也是多模块的硬阻塞点 |
| `data/repository/ChaoxingRepository.kt` | 2275 | 超星核心 API |
| `ui/screen/market/MarketViewModel.kt` | 1410 | |
| `ui/screen/home/HomeViewModel.kt` | 1332 | 跨域聚合（余额+支付码+课程+任务） |
| `ui/screen/chaoxing/ChaoxingViewModel.kt` | 1330 | |
| `ui/screen/profile/ProfileScreen.kt` | 1285 | 我的页聚合，含多个二级入口（拆分后） |
| `ui/screen/schedule/ScheduleViewModel.kt` | 1189 | |
| `ui/screen/market/MarketComponents.kt` | 790 | 集市共享组件（拆分后） |
| `ui/screen/chaoxing/ChaoxingTabScreen.kt` | 326 | 超星 4 个二级 tab 容器（拆分后） |
| 其它 >700：`HomeScreen 1054`/`ChaoxingSettingsScreen 1022`/`DashboardScreen 872`/`TrainingPlanScreen 841`/`CardAnalyticsScreen 771`/`YcardRepository 764`/`XzxxScreen 744`/`ScheduleScreen 730`/`MarketSettingsScreen 730`/`ChaoxingStudyRepository 715`/`EmptyClassroomScreen 710`/`GradeScreen 708`/`MarketExportUtils 703`/`MarketRepository 701` | | |

## :core — 共享核心（所有领域依赖；多模块时下沉到 :core）

- `data/local/SessionManager.kt` `AppDataStore.kt` — 单一 DataStore 持久化，全域缓存 key（**待按域拆分**）
- `data/network/` — OkHttp 工厂、`SessionAuthenticator`(自动续期)、`SecureHttpClientFactory`、`Tls12OnlySocketFactory`、`ResilientDns`(抗 DNS 污染)
- `data/remote/` — JSON 工具
- `ui/theme/` — Color/Type/Shape/Spacing/Gradient token
- `ui/components/` — 跨页共享 Composable
- `util/` — DES/AES 加密、`TtfGlyphParser`/`CxFontDecoder` 字体解码、`OverlayWindow` 悬浮窗、`DebugClock` 时间注入
- `data/repository/CasAuthRepository.kt` — CAS SSO 登录基座（portal/jw 会话都从它派生）
- `data/repository/AnnouncementRepository.kt` — 开发者公告（Gitee 零登录拉取）
- `data/update/` `UpdateManager` — Gitee 版本检查/下载/安装

## :feature-jw — 教务（CAS→jw SESSION；key 前缀 jw_/schedule_/grades_/exams_/training_plan_/empty_classroom_/assessment_/record_/homework_/user_tasks_/exam_predictions_）

- repos：`JwAuthRepository`(jw SSO 基座) `CourseRepository`(课表) `GradeRepository`(成绩) `ExamRepository`(考试安排) `ExamDataRepository`(排考预测,Gitee) `TrainingPlanRepository` `ProgramCompletionRepository`(培养方案完成度) `EmptyClassroomRepository` `JwcNoticeRepository`(教务通知) `AssessmentRepository`(课程考核) `RecordRepository`(上课记录) `HomeworkRepository`(课程作业) `UserTaskRepository`(待办)
- screens：`schedule/`(课表,含 sections/ components/) `grade/` `exam/`(含排考预测) `trainingplan/` `emptyclassroom/` `dashboard/`(今日课程+任务+教务通知)

## :feature-portal — 一卡通/门户（CAS→one.ahu JSESSIONID / ycard JWT / adwmh JSESSIONID；key 前缀 student_info_/finance_/attendance_/kqcard_/bills_/adwmh_/ac_/lighting_/new_campus_/bathroom_）

- repos：`CardRepository`(实时余额) `YcardRepository`(水电费/账单) `FinanceRepository`(财务) `KqAttendanceRepository`(考勤) `StudentInfoRepository`(学生信息) `StudentTableClient`(tp_ep_stu 公共客户端) `XzxxRepository`(学籍/行政信息) `AdwmhCardRepository`(智慧安大支付码,仅 TLS1.2)
- screens：`home/`(余额+支付码,聚合) `attendance/` `profile/` 的 `CardAnalyticsScreen`/`XzxxScreen`/Finance/StudentInfo 部分

## :feature-market — 校园集市（独立 Bearer JWT；key 前缀 market_/ai_comment_）

- repos：`MarketRepository` `AiCommentRepository`(AI 点评)
- screens：`market/`（List/Hot/Detail/Notices/Settings/Compose + Components/ExportUtils/ViewModel）

## :feature-cprog — 大学计算机平台（C 语言在线评测,内网 http;独立学号+身份证后6位+验证码;key 前缀 cprog_）

- repos：`CProgAuthRepository`(登录闭环:redirect/login→kaptcha→login/get→login/unified,dfgdfg=jwt1.sub;JSESSIONID+JWT 持久化) `CProgRepository`(只读:section/query·getSubjects·exams/search/query 分页·assign/paper3+paper/message 整卷)
- screens：`cprog/`（Screen 容器 + Login/List/Paper 三页 + ViewModel 状态机）。入口挂 AppHub「学习」组「大学计算机平台」
- 合规：仅练习(lianxi)进卷看答案;考试/测试进卷=真开考+监考+耗次数,UI 不放行
- 网络：内网 172.17.106.232:8080 明文,network_security_config 白名单;baseUrl 登录页高级设置可配

## :feature-chaoxing — 超星学习通（独立手机号+密码；key 前缀 cx_）

- repos：`ChaoxingRepository`(核心 API) `ChaoxingStudyRepository`(自动学习引擎) `ChaoxingTikuRepository`(题库链+答案标准化) `ChaoxingNotificationRepository`(完成通知)
- screens：`chaoxing/`（14 文件：Tab/Main/Login/Study/Tiku/Settings/CourseDetail + `sign/` 签到组件）
- service：`service/ChaoxingStudyService.kt`(前台服务)

## :app — 应用壳与跨域聚合（依赖所有 feature；难以下沉，留在顶层）

- `MainActivity.kt` `AhuPlusApplication.kt`(手动 DI 容器，引用全部 repo) `data/repository/InitCoordinator.kt`(首登串行预热 7 域)
- `ui/navigation/AppNavigation.kt` `ui/screen/main/`(MainScreen+5 tab) `login/` `autologin/` `apps/`(AppHub)
- `notification/`(Widget 调度+课程提醒+BootReceiver) `ui/widget/`
- **跨域聚合页**（多模块时的痛点，触碰多个 feature）：`home/HomeViewModel`(余额+支付码+课程+任务) `dashboard/DashboardScreen` `profile/ProfileScreen`

## 多模块依赖方向

```
:app ──► :feature-jw ─┐
  │   ──► :feature-portal ─┤
  │   ──► :feature-market ─┼──► :core
  │   ──► :feature-chaoxing ┘
  └──► :core
```

阻塞点：① `SessionManager` 持全域 key → 每个 feature 都被迫依赖它，**必须先按域拆分** ② 聚合页(home/dashboard/profile)跨多 feature → 留 :app ③ `AhuPlusApplication`/`InitCoordinator` 引用全部 repo → 顶层 :app。
