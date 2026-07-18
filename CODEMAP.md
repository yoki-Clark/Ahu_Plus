# CODEMAP

本文是当前源码的文件级导航。路径均相对 `app/src/main/java/com/ahu_plus/`，内容只描述仓库中已经存在的实现。

## 入口与组装

| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | Edge-to-edge Activity、主题订阅、更新/公告弹窗、通知与 `ahuplus://market/import` 深链 |
| `AhuPlusApplication.kt` | Conscrypt 初始化、手动 DI、Repository 状态恢复和账号数据清理 |
| `ui/navigation/AppNavigation.kt` | `login`/`main` NavHost、静默登录、显式重认证、退出登录 |
| `ui/screen/main/MainScreen.kt` | 顶层 6 个候选入口、响应式 NavigationBar/NavigationRail、所有业务页面状态机 |
| `data/home/AppRegistry.kt` | 应用聚合页与最近使用的 21 个入口元数据 |

## 数据基础设施

| 目录/文件 | 职责 |
|---|---|
| `data/local/AppDataStore.kt` | 单例 `ahu_plus_prefs` DataStore、课程备注和考核方案 |
| `data/local/SessionManager.kt` | 会话内存镜像、普通偏好、业务缓存、迁移和清理策略 |
| `data/local/EncryptedCredentialStore.kt` | Keystore 支持的账号、会话、token、API key 加密存储 |
| `data/GsonProvider.kt` | 全局 Gson 配置 |
| `data/network/SecureHttpClientFactory.kt` | 系统证书、按需 trust-all、TLS 1.2、CookieJar 和超时策略 |
| `data/network/SessionAuthenticator.kt` | 失败请求的会话恢复协调 |
| `data/network/ResilientDns.kt` | DNS 解析和伪 IP 过滤 |
| `data/network/CancellableCall.kt` | 可取消 OkHttp 调用桥接 |
| `data/network/ChaoxingTrafficGovernor.kt` | 学习通请求节流、退避和状态 |
| `data/local/DataRefreshPolicy.kt` | 缓存新鲜度判断 |
| `data/local/DataSnapshotStatus.kt` | UI 数据来源/时间状态 |

## 校园账号与教务

| 能力 | Repository | UI/状态 |
|---|---|---|
| CAS | `CasAuthRepository.kt` | `login/`、`autologin/` |
| 教务 Web 会话 | `JwAuthRepository.kt` | 多个教务 ViewModel 共用 |
| 课表/学期 | `CourseRepository.kt` | `schedule/` |
| 成绩/GPA | `GradeRepository.kt` | `grade/` |
| 考试 | `ExamRepository.kt` | `exam/` |
| 培养方案 | `TrainingPlanRepository.kt` | `trainingplan/` |
| 毕业完成度 | `ProgramCompletionRepository.kt` | 培养方案详情 |
| 空教室 | `EmptyClassroomRepository.kt` | `emptyclassroom/` |
| 教务移动端认证 | `JwAppAuthRepository.kt` | `roomcoursetable/` |
| 教室课表 | `RoomCourseTableRepository.kt` | `roomcoursetable/` |
| 教学评价 | `EvaluationRepository.kt` | `evaluation/` |

## 门户、一卡通与个人数据

| 能力 | Repository | UI/状态 |
|---|---|---|
| 校园卡门户余额 | `CardRepository.kt` | `home/HomeViewModel.kt` 的支付码余额兜底 |
| 一卡通账单/生活缴费 | `YcardRepository.kt` | `home/`、`profile/BillScreens.kt`、`UtilityDetailScreens.kt` |
| 充值 | `YcardPayRepository.kt` | `home/DepositSheet.kt` |
| 智慧安大支付码 | `AdwmhCardRepository.kt` | `home/CampusQrCodeCard.kt` |
| 考勤 | `KqAttendanceRepository.kt` | `attendance/`、课表详情、Profile |
| 学生一张表公共客户端 | `StudentTableClient.kt` | StudentInfo/Finance 共享 |
| 学生信息 | `StudentInfoRepository.kt` | `profile/MyInfoScreens.kt` |
| 财务汇总 | `FinanceRepository.kt` | `profile/FinanceViewModel.kt` |
| 校长信箱 | `XzxxRepository.kt` | `profile/XzxxScreen.kt` |

## 本地学习与日程

| 文件 | 职责 |
|---|---|
| `data/agenda/AgendaBuilder.kt` | 聚合课程、考试、自定义日程 |
| `data/calendar/SystemCalendarSync.kt` | 写入和移除系统日历事件 |
| `data/repository/AssessmentRepository.kt` | 课程考核方案 |
| `data/repository/RecordRepository.kt` | 课程记录 |
| `data/repository/HomeworkRepository.kt` | 本地作业 |
| `data/repository/UserTaskRepository.kt` | 用户待办 |
| `ui/screen/agenda/` | 日程时间线和编辑器 |
| `ui/screen/dashboard/` | 首页课程、日程、收藏和教务通知聚合 |

## 第三方服务

### 校园集市

- 网络：`data/repository/MarketRepository.kt`
- URL/节点：`data/remote/market/MarketApi.kt`
- 请求头：`data/remote/market/MarketHeaders.kt`
- AI：`data/repository/AiCommentRepository.kt`
- UI：`ui/screen/market/`

### 超星学习通

- 核心 API：`ChaoxingRepository.kt`
- 题库：`ChaoxingTikuRepository.kt`
- 学习执行：`ChaoxingStudyRepository.kt`
- 外部通知：`ChaoxingNotificationRepository.kt`
- UI：`ui/screen/chaoxing/`
- 前台服务：`service/ChaoxingStudyService.kt`
- 字体：`util/CxFontDecoder.kt`、`util/TtfGlyphParser.kt`、`assets/font_map_table.json`

### WeLearn

- 认证：`WeLearnAuthRepository.kt`
- 课程数据：`WeLearnRepository.kt`
- 课件答案：`WeLearnAnswerRepository.kt`
- 学习执行：`WeLearnStudyRepository.kt`
- UI：`ui/screen/welearn/`
- 前台服务：`service/WeLearnStudyService.kt`

### 大学计算机平台

- 认证和直连/WebVPN 传输：`CProgAuthRepository.kt`、`CProgWebVpnAuthenticator.kt`
- 业务：`CProgRepository.kt`、`CProgResponseParser.kt`
- UI：`ui/screen/cprog/`

## 公共内容、天气与升级

| 能力 | 文件 |
|---|---|
| 教务通知 | `JwcNoticeRepository.kt`、`ui/screen/dashboard/Jwc*` |
| WAF Cookie | `data/local/JwcWafCookieStore.kt`、`XzxxWafCookieStore.kt` |
| 统一消息中心 | `ui/screen/messages/UnifiedMessageCenterScreen.kt` |
| 天气 | `WeatherRepository.kt`、`data/weather/`、`ui/screen/weather/` |
| 开发者公告 | `AnnouncementRepository.kt`、`data/announcement/AnnouncementManager.kt` |
| 应用升级 | `data/update/UpdateManager.kt`、`ui/components/UpdateDialog.kt` |
| 开发者诊断 | `data/developer/`、`ui/screen/developer/` |

## 通知、Widget 与平台组件

- `notification/CourseReminderScheduler.kt` / `CourseReminderReceiver.kt`
- `notification/AgendaReminderScheduler.kt` / `AgendaReminderReceiver.kt`
- `notification/BootReceiver.kt`
- `notification/WidgetUpdateScheduler.kt`
- `notification/CampusCardAlertNotifier.kt`
- `ui/widget/TodayScheduleWidget.kt`

`AndroidManifest.xml` 当前声明 5 个 receiver、2 个前台 service、1 个 FileProvider 和 1 个 Activity。

## 主题与组件

- `ui/theme/Color.kt`、`Theme.kt`、`Type.kt`
- `ui/theme/Shape.kt`、`Spacing.kt`、`Gradient.kt`
- `ui/theme/CourseColors.kt`、`MarketColors.kt`
- `ui/components/`：刷新、状态页、折叠区、图片选择、公告/更新弹窗、天气面板

## 体积热点

按当前行数，修改前应先定位函数而不是整文件通读：

| 文件 | 约行数 |
|---|---:|
| `data/local/SessionManager.kt` | 4,046 |
| `data/repository/ChaoxingRepository.kt` | 2,692 |
| `ui/screen/home/HomeViewModel.kt` | 1,876 |
| `ui/screen/chaoxing/ChaoxingViewModel.kt` | 1,749 |
| `ui/screen/market/MarketViewModel.kt` | 1,596 |
| `ui/screen/profile/ProfileScreen.kt` | 1,478 |
| `ui/screen/main/MainScreen.kt` | 1,146 |
| `ui/screen/schedule/ScheduleViewModel.kt` | 1,114 |

## 测试地图

JVM 测试位于 `app/src/test/java/com/ahu_plus/`，覆盖数据刷新策略、网络客户端、认证解析、通知策略、开发者诊断、教务/集市/天气/WeLearn 解析和 UI 纯逻辑。设备测试入口位于 `app/src/androidTest/`。

```powershell
.\gradlew.bat :app:testDebugUnitTest
```
