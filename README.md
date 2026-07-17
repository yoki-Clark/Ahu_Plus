# Ahu_Plus

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Compose-2026.02-4285F4)
![Version](https://img.shields.io/badge/release-v2.2.1.2-2563eb)
[![Gitee](https://img.shields.io/badge/Gitee-yao--enqi%2FAhu__Plus-c71d23?logo=gitee)](https://gitee.com/yao-enqi/Ahu_Plus)
[![GitHub](https://img.shields.io/badge/GitHub-yoki--Clark%2FAhu__Plus-181717?logo=github)](https://github.com/yoki-Clark/Ahu_Plus)

> 安徽大学校园助手 Android App,把 CAS 后面的教务 / 一卡通 / 学习通 / 智慧安大 / 集市 聚合到一个原生 App。
> 非官方,与安徽大学无关。仅供学习交流。

<p align="center"><img src="screen.png" width="320"/></p>

## 功能

- **教务** — 课表(多学期、导出到相册)、成绩 / GPA、考试、培养方案、空闲教室、排考预测(Gitee 拉 JSON)
- **一卡通** — 余额、消费账单、水电费、**网费充值**(`feeitemid=431`)、智慧安大支付码(仅 TLS 1.2)、考勤、学籍
- **学习通** — 自动刷课(6 类任务: document > read > audio > live > workid > video)、6 类签到(含九宫格手势、GPS)、字体反混淆解码、`font_map_table.json` 30,884 条目
- **题库链** — `CACHE → YANXI → GO → LIKE → ADAPTER → AI → SILICONFLOW`,AI 默认 DeepSeek
- **集市** — 列表 / 详情 / 发布 / AI 点评,Bearer JWT
- **小工具** — Glance 桌面 Widget、课程提醒、Open-Meteo 首页天气 + 独立天气屏、WeLearn(外研社 SFLEP)刷时长 + 选择性刷题
- **跨域** — 开发者公告(Gitee 零登录,启动弹窗 + 历史列表)、使用帮助、数据驱动

## 技术栈

Kotlin 2.2.10 / Compose BOM 2026.02.01 (Material 3) / AGP 9.2.1 / Gradle 9.4.1 / Java 11(开启 `coreLibraryDesugaring`)
OkHttp + Conscrypt(`Security.insertProviderAt` 启动期全局接管)+ `ResilientDns`(过滤 `198.18.0.0/15` 伪 IP)
DataStore Preferences / Gson / Jsoup / ZXing / Coil(含 SVG)/ `androidx.glance`
单 Activity + Navigation Compose + 手动 DI;minSdk 24 / targetSdk 36

## 构建

```bash
./gradlew assembleDebug              # arm64-v8a Debug(~29 MB)
./gradlew :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Windows 用 `gradlew.bat`。模拟器装 `app-universal-debug.apk`。

Release 签名走 `local.properties`:

```properties
AHU_RELEASE_STORE_FILE=/path/to/your.jks
AHU_RELEASE_STORE_PASSWORD=...
AHU_RELEASE_KEY_ALIAS=...
AHU_RELEASE_KEY_PASSWORD=...
```

未配置回退本机 `debug.keystore`,**禁止分发**。Fork 后务必自签 + 改 `applicationId`。

## 认证

| 端点 | 域名 | 认证 |
|---|---|---|
| 余额 / 学籍 / 一张表 | `one.ahu.edu.cn` | CAS → JSESSIONID |
| 教务 | `jw.ahu.edu.cn` | CAS → SESSION |
| 账单 / 水电费 / 网费 | `ycard.ahu.edu.cn` | CAS → JWT(`synjones-auth: bearer …`),网费走独立 blade-pay |
| 智慧安大支付码 | `adwmh.ahu.edu.cn` | CAS → JSESSIONID(**仅 TLS 1.2**,2-3 次/分限速) |
| 考勤 | `kqcard.ahu.edu.cn` | CAS → JSESSIONID |
| 集市 | `api.zxs-bbs.cn` | Bearer JWT |
| 学习通 | `passport2.chaoxing.com` + mooc1 + mooc2 | 手机号 + 密码 AES |
| 排考 / 公告 / 升级 / 天气 / WeLearn 答案 | `gitee.com` / `open-meteo.com` / `sflep.com` | 无 |

CAS 流程见 [CasAuthRepository.kt](app/src/main/java/com/yourname/ahu_plus/data/repository/CasAuthRepository.kt#L19-L27);续期走 `SessionAuthenticator` 被动嗅探。`*.ahu.edu.cn` 自签名证书,`SecureHttpClientFactory.create(trustAll, tls12Only)` 显式声明防误用扩散;`passport2` / `api.zxs-bbs.cn` / `open-meteo` 都是标准证书,`trustAll=false`。校长信箱仅在 WAF 凭证缺失或过期时短暂使用隐藏 WebView,列表/详情/验证码/提交均走 OkHttp。

## 项目结构

```
app/src/main/java/com/yourname/ahu_plus/
├── MainActivity.kt            唯一 Activity
├── AhuPlusApplication.kt      手动 DI + Conscrypt 注入
├── data/
│   ├── local/                 SessionManager + AppDataStore(单一 DataStore,150+ key)
│   ├── model/ network/ remote/ repository/   见下方
│   ├── debug/                 DebugClock(时间注入)
│   ├── home/ weather/         跨域聚合
│   ├── announcement/          AnnouncementManager(启动弹窗)
│   └── update/                UpdateManager(Gitee 升级)
├── service/                   ChaoxingStudyService + WeLearnStudyService(ForegroundService)
├── notification/              WidgetUpdateScheduler + 课程提醒 + BootReceiver
├── ui/
│   ├── navigation/            NavHost(login → 5 tabs)
│   ├── screen/                login/home/dashboard/schedule/market/grade/exam/...
│   ├── widget/                Glance TodayScheduleWidget
│   └── theme/                 Material3 Color/Type/Shape/Spacing/Gradient
└── util/                      DES/AES, TtfGlyphParser, CxFontDecoder, OverlayWindow
```

文件级地图 [CODEMAP.md](CODEMAP.md)。`SessionManager.kt` 3,361 行是头号体积热点,多模块拆分前是硬阻塞。

## 已知限制

- 登录无 CAPTCHA 自动化(首次绑定需微信)
- 会话被动续期,无主动 ping
- Android 13+ `POST_NOTIFICATIONS` / 14+ `SCHEDULE_EXACT_ALARM` / 悬浮窗 `SYSTEM_ALERT_WINDOW` 需用户手动授予
- 超星自动刷课违反超星服务条款,风险自担

完整威胁模型见 [SECURITY.md](SECURITY.md)。

## 隐私

凭据仅本地 DataStore。App 仅向 安大官方系统 / 用户主动配置的第三方(集市 JWT、AI key 等)/ 公开数据源(Gitee 排考 + 公告 + 升级 / Open-Meteo / SFLEP) 发数据。

`.env` 中可放 `AHU_TEST_USERNAME` / `AHU_TEST_PASSWORD` / `MARKET_TEST_TOKEN`,不要提交真实凭据。维护工具在 `tools/`(活跃脚本在根,归档 `_archive/`,公告模板 `announcements/`)。

## 致谢 / 许可

移植自 [Samueli924/chaoxing](https://github.com/Samueli924/chaoxing)(GPL-3.0)。网络层借鉴 AHUTong-master。[GPL-3.0](LICENSE)。
