# Ahu_Plus

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Compose_BOM-2026.02.01-4285F4)
![Version](https://img.shields.io/badge/version-2.2.2.6-2563eb)

安徽大学校园助手 Android 应用。项目把学校门户、教务、一卡通、学习平台和若干公开数据源整合到一个 Jetpack Compose 客户端中。

本项目非安徽大学官方产品，与安徽大学无隶属关系。

## 当前能力

### 校园账号

- CAS 登录与静默续期
- 首页概览、校园卡余额、账单与消费分析
- 浴室余额、空调/照明电费、网费查询与充值
- 智慧安大支付码
- 课表、多学期切换、课表导出、课程备注和课程提醒
- 成绩、GPA、考试、培养方案、毕业完成度
- 空教室、教室课表、教学评价、考勤记录
- 学生信息、财务汇总
- 教务通知、校长信箱
- 日程、系统日历同步、桌面课表 Widget

### 可选第三方服务

- 校园集市：多身份、列表/详情/评论/发帖、通知、搜索、内容导出、AI 评论
- 超星学习通：课程、章节、作业、消息、签到和后台学习服务
- WeLearn：课程树、学习进度、答题数据和后台学习服务
- 大学计算机平台：直接校园网或 WebVPN 登录、练习/考试记录与试卷查看

第三方服务总开关及底栏入口由用户控制。首页、应用、我的固定显示；集市、学习通、WeLearn 是 3 个候选服务，其中最多固定 2 个，因此顶层最多同时显示 5 个入口。

### 公开数据

- Gitee：版本清单、开发者公告
- Open-Meteo：合肥蜀山区天气和空气质量

## 技术栈

| 项目 | 当前配置 |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3，Compose BOM 2026.02.01 |
| 构建 | Gradle 9.4.1、AGP 9.2.1、Daemon JVM 21 |
| Android | compileSdk/targetSdk 36，minSdk 24 |
| Java 字节码 | Java 11，启用 core library desugaring |
| 网络 | OkHttp 4.12、Conscrypt、Jsoup |
| 本地状态 | Preferences DataStore、EncryptedSharedPreferences、Gson |
| 图片/扫码 | Coil、SVG、ZXing、CameraX |
| Widget | AndroidX Glance |

项目是单 Activity、Navigation Compose、手动依赖注入架构。应用代码的 `namespace` 是 `com.ahu_plus`；为兼容已安装版本，对外 `applicationId` 仍是 `com.yourname.ahu_plus`。两者不要混用。

## 构建与测试

要求：JDK 21 可供 Gradle Daemon 使用，Android SDK 36 已安装。

```bash
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
```

Windows：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

ABI split 会生成：

- `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- `app/build/outputs/apk/debug/app-universal-debug.apk`

真机通常安装 arm64 包；模拟器或未知 ABI 使用 universal 包。

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Release 签名从未提交的 `local.properties` 读取：

```properties
AHU_RELEASE_STORE_FILE=/absolute/path/to/release.jks
AHU_RELEASE_STORE_PASSWORD=...
AHU_RELEASE_KEY_ALIAS=...
AHU_RELEASE_KEY_PASSWORD=...
```

未配置时会回退到本机 debug keystore，仅用于本地验证，不能用于分发。

## 代码结构

```text
app/src/main/java/com/ahu_plus/
├── MainActivity.kt              # Activity、主题、更新/公告弹窗、深链入口
├── AhuPlusApplication.kt        # 手动 DI、全局初始化、仓库状态恢复
├── data/
│   ├── local/                   # DataStore、加密凭据、缓存与偏好
│   ├── model/                   # 业务模型
│   ├── network/                 # HTTP 客户端、DNS、会话重试、流量治理
│   ├── repository/              # 各系统认证与数据访问
│   ├── agenda/ calendar/        # 日程构建与系统日历同步
│   ├── announcement/ update/    # 公告和升级
│   ├── developer/               # 开发者中心与诊断
│   └── home/ weather/           # 应用注册表与天气聚合
├── notification/               # 课程/日程提醒、开机重排、Widget 调度
├── service/                    # 学习通与 WeLearn 前台服务
├── ui/
│   ├── navigation/             # login/main 两级 NavHost
│   ├── screen/                 # 业务界面与 ViewModel
│   ├── components/             # 公共 Compose 组件
│   ├── theme/                  # Material 3 设计 token
│   └── widget/                 # Glance 今日课表 Widget
└── util/                       # DES/AES、字体解析、支付签名、分享等
```

文件级索引见 [CODEMAP.md](CODEMAP.md)，主题文档入口见 [docs/Ahu-Plus-总览.md](docs/Ahu-Plus-总览.md)。

## 认证与网络

| 系统 | 主域名 | 认证/会话 |
|---|---|---|
| 校园门户/学生一张表 | `one.ahu.edu.cn` | CAS，`JSESSIONID` |
| 教务 Web | `jw.ahu.edu.cn` | CAS，`SESSION`/`PSTSID` |
| 教务移动端 | `jwapp.ahu.edu.cn` | 独立账号选择与 token |
| 一卡通 | `ycard.ahu.edu.cn` | CAS，JWT + Cookie |
| 智慧安大支付码 | `adwmh.ahu.edu.cn` | 独立登录，`JSESSIONID`，TLS 1.2 |
| 考勤 | `kqcard.ahu.edu.cn` | CAS，Cookie |
| 集市 | `api.zxs-bbs.cn` | 用户导入 Bearer JWT |
| 学习通 | `*.chaoxing.com` | 手机号/学号 + 密码，Cookie |
| WeLearn | `welearn.sflep.com`/`sso.sflep.com` | 独立账号，OIDC/Cookie |
| 大学计算机平台 | 内网地址或 `wvpn.ahu.edu.cn` | 独立账号、验证码、JWT/Cookie |

学校部分 HTTPS 端点使用客户端无法验证的证书，调用处必须显式选择 `trustAll = true`；普通公网服务必须保持系统证书校验。详细边界见 [SECURITY.md](SECURITY.md)。

## 本地数据

- 普通设置和业务缓存保存在 `ahu_plus_prefs` DataStore。
- 账号、密码、会话、Bearer token 和 API key 保存在 Android Keystore 支持的 `EncryptedSharedPreferences`。
- 启动时会迁移旧版 DataStore 中的明文敏感键；加密存储不可用时，敏感值只保留在当前进程内，不回退明文持久化。
- 退出校园账号会清理校园账号数据、JWApp、学习通、WeLearn 和 CProg 账号会话；集市身份、普通设置和课程备注保留。“清除全部数据”路径会移除全部可清理凭据和缓存。

## 已知边界

- `CourseRepository.DEFAULT_SEMESTER_ID` 仍为 `112`，虽然 UI 支持拉取学期列表，但默认回退值需要随教务数据变化验证。
- 学习通的签到码、二维码和拍照签到仍有待真机抓包校准的分支，源码中保留了明确 TODO。
- 大学计算机平台的直接地址是校内明文 HTTP；自定义地址还受 Android 网络安全白名单约束。
- 课程/日程精确提醒受系统通知和精确闹钟权限影响；权限缺失时会降级，可能延迟。
- 自动学习、自动答题或自动签到可能违反对应平台规则，风险由使用者承担。

当前可确认问题见 [BUG_REVIEW.md](BUG_REVIEW.md)，内测流程见 [BETA_TESTING.md](BETA_TESTING.md)。

## 安全与贡献

- 不要提交 `.env`、`local.properties`、keystore、token、Cookie、个人 API 响应或包含学生信息的抓包产物。
- 测试账号只通过 `AHU_TEST_USERNAME`、`AHU_TEST_PASSWORD` 等本地环境变量提供。
- 网络代码必须关闭响应体并在 IO 调度器执行。
- 修改认证、缓存、提醒或升级逻辑时必须补充对应单元测试。

漏洞报告方式见 [SECURITY.md](SECURITY.md)。

## 许可

项目包含从 [Samueli924/chaoxing](https://github.com/Samueli924/chaoxing) 移植和演化的代码，以 [GPL-3.0](LICENSE) 发布。Fork 和重新分发必须遵守 GPL-3.0，并使用自己的签名与应用标识。
