# Ahu_Plus Context

本文记录跨模块长期稳定的领域词汇。实现事实仍以源码、构建配置和自动化测试为准。

## 发布领域

- **构建版本（build version）**：当前源码编译进 APK 的 `versionName` 与 `versionCode`，来自 `release/release-state.json`。
- **候选版本（candidate）**：准备进入某一渠道但尚未修改公开清单的构建版本和发布说明。
- **已发布渠道（published channel）**：stable 或 beta 当前公开可下载的不可变 APK 及其元数据。
- **签名身份（signing identity）**：Android 用于判断覆盖升级资格的证书。Ahu_Plus 的历史身份由证书 SHA-256 唯一确认，证书显示名不是判断依据。
- **可分发 APK（distributable APK）**：通过签名、包名、版本、SDK、ABI、zipalign、大小和 SHA-256 全部校验的 Release APK。
- **dry-run**：本地构建并验证候选 APK、生成预览清单，但不更新公开清单、不修改 Git 状态、不访问发布 API。
- **晋升（promotion）**：远端资产人工上传并确认后，将已验证候选记录为某渠道的已发布版本。

## 数据与运行领域

- **凭据**：密码、Cookie、token、API key 和会话，只能进入 Keystore 支持的本地存储，不可导出。
- **用户资产**：用户主动创建且需要跨账号退出保留的数据，例如备注、待办、作业和图片。
- **业务缓存**：可由远端重新获取的数据；损坏时只清理所属业务域。
- **后台作业**：具有排队、运行、终态、取消和恢复语义的长任务，不等同于精确提醒。
- **能力控制**：只能降级或关闭能力的签名配置，不得远程开启本地关闭的高风险功能。
