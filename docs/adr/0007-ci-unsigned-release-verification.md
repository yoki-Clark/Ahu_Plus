# ADR-0007：CI 无签名 Release 构建仅用于 R8 验证

- 状态：Accepted
- 关联：[ADR-0001](./0001-release-signing-and-state.md)

## 背景

ADR-0001 规定 Release 缺少完整签名配置或指纹不匹配时必须在打包前失败。该策略由
`app/build.gradle.kts` 中的 `validateReleaseSigning` 任务强制执行，并在本地发布流程
（`tools/release/release.py`）中生效。但 CI 没有（也不应该有）发布密钥，导致
`assembleRelease` 在 CI 上无法执行，R8/混淆/资源收缩路径的问题只能在合入后才能由
发布流程暴露。

## 决策

1. CI 构建 `assembleRelease` 产出未签名 APK，仅用于暴露 R8/混淆/资源收缩错误；
   未签名 APK 不可安装、不可分发。
2. `validateReleaseSigning` 只在签名配置完整时校验 keystore 与 allowlist；无签名配置
   （CI、本地无 `local.properties` 签名键）时跳过，允许产出 `*-release-unsigned.apk`。
3. 签名校验只挂在涉及"签名产物输出"的任务上（`packageRelease`、
   `signReleaseBundle`、`bundle/makeApk/zipApks*Release*`），`assembleRelease` 本身
   不再直接依赖校验任务。
4. `validateReleaseSigning` 显式不兼容 Gradle 配置缓存：校验闭包引用脚本级签名配置，
   避免 keystore 密码被序列化进本地配置缓存文件。
5. 可分发签名包仍只能通过本地 `tools/release/release.py`（完整签名配置 +
   allowlist 校验）产出；CI 不保存发布密钥。

## 影响

- CI 的 Release 构建步骤成为依赖升级、模型改动前的回归保护；R8 错误在 PR 阶段即可发现。
- 本地 `./gradlew assembleRelease` 无签名配置时成功但产物为 `-unsigned` 后缀；
   Android 系统拒绝安装此类 APK，属于预期行为。
- 本地签名构建与 `release.py` 流程不变：`packageRelease` 仍先执行
   `validateReleaseSigning`，指纹不匹配时打包前失败。
- Release 构建因配置缓存不兼容而回退到常规配置流程，构建时间略有增加，可接受。
